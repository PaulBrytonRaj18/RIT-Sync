package com.ritsync.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class GeofenceService {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    public double distanceMetres(double lat1, double lng1, double lat2, double lng2) {
        double dLat = toRad(lat2 - lat1);
        double dLng = toRad(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2))
                 * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    public boolean isWithinFence(double userLat, double userLng,
                                  BigDecimal fenceLat, BigDecimal fenceLng,
                                  int radiusMetres) {
        double dist = distanceMetres(
            userLat, userLng,
            fenceLat.doubleValue(), fenceLng.doubleValue()
        );
        return dist <= radiusMetres;
    }

    private double toRad(double degrees) {
        return degrees * Math.PI / 180.0;
    }
}
