package com.ritsync.service;

import com.ritsync.entity.AttendanceRecord;
import com.ritsync.entity.Session;
import com.ritsync.repository.AttendanceRecordRepository;
import com.ritsync.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class HeatmapAnalyticsService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final SessionRepository sessionRepository;
    private static final double DBSCAN_EPS = 5.0;
    private static final int DBSCAN_MIN_PTS = 2;

    public Map<String, Object> getSessionHeatmap(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        List<AttendanceRecord> records = attendanceRecordRepository.findBySessionId(sessionId).stream()
                .filter(r -> r.getUserLat() != null && r.getUserLng() != null)
                .filter(r -> "PRESENT".equals(r.getStatus().name()))
                .toList();

        List<double[]> points = records.stream()
                .map(r -> new double[]{r.getUserLng(), r.getUserLat()})
                .toList();

        List<List<double[]>> clusters = dbscanClustering(points);
        String insights = generateInsights(clusters, points.size(), session);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("geofenceCenter", new double[]{session.getGeofenceLng().doubleValue(), session.getGeofenceLat().doubleValue()});
        result.put("geofenceRadius", session.getGeofenceRadiusM());
        result.put("points", points);
        result.put("clusters", clusters);
        result.put("totalStudents", points.size());
        result.put("clusterCount", clusters.size());
        result.put("insights", insights);
        result.put("timestamp", System.currentTimeMillis());

        return result;
    }

    private List<List<double[]>> dbscanClustering(List<double[]> points) {
        List<List<double[]>> clusters = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Set<Integer> clustered = new HashSet<>();

        for (int i = 0; i < points.size(); i++) {
            if (visited.contains(i)) continue;
            visited.add(i);

            List<Integer> neighbors = getNeighbors(points, i);
            if (neighbors.size() < DBSCAN_MIN_PTS) continue;

            List<double[]> cluster = new ArrayList<>();
            cluster.add(points.get(i));
            clustered.add(i);

            Queue<Integer> queue = new LinkedList<>(neighbors);
            while (!queue.isEmpty()) {
                int q = queue.poll();
                if (!visited.contains(q)) {
                    visited.add(q);
                    List<Integer> qNeighbors = getNeighbors(points, q);
                    if (qNeighbors.size() >= DBSCAN_MIN_PTS) {
                        queue.addAll(qNeighbors);
                    }
                }
                if (!clustered.contains(q)) {
                    cluster.add(points.get(q));
                    clustered.add(q);
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    private List<Integer> getNeighbors(List<double[]> points, int idx) {
        List<Integer> neighbors = new ArrayList<>();
        double[] p = points.get(idx);
        for (int i = 0; i < points.size(); i++) {
            if (i == idx) continue;
            double[] q = points.get(i);
            double dist = haversine(p[1], p[0], q[1], q[0]);
            if (dist <= DBSCAN_EPS) neighbors.add(i);
        }
        return neighbors;
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng/2) * Math.sin(dLng/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    private String generateInsights(List<List<double[]>> clusters, int total, Session session) {
        if (total == 0) return "No attendance records with GPS data for this session.";

        double avgClusterSize = clusters.stream().mapToInt(List::size).average().orElse(0);
        long isolatedStudents = total - clusters.stream().mapToInt(List::size).sum();

        StringBuilder insights = new StringBuilder();
        insights.append(String.format("• %d students checked in with valid GPS%n", total));
        insights.append(String.format("• %d student clusters detected (avg size: %.1f)%n", clusters.size(), avgClusterSize));

        if (isolatedStudents > 0) {
            insights.append(String.format("• %d isolated students (not part of any cluster)%n", isolatedStudents));
        }

        if (clusters.size() > 3) {
            insights.append("• Classroom is well-distributed%n");
        } else if (clusters.size() == 1 && total > 5) {
            insights.append("• Large group detected - possible social clustering%n");
        }

        return insights.toString();
    }
}
