import { useState, useEffect, useRef } from 'react';
import { DeckGL } from '@deck.gl/react';
import { HeatmapLayer } from '@deck.gl/aggregation-layers';
import { getHeatmap, subscribeToHeatmap } from '../../api/analytics';
import { LoadingSpinner } from '../../components/ui/LoadingSpinner';
import PageHeader from '../../components/shared/PageHeader';

/**
 * Classroom Spatial Heatmap Dashboard
 * Real-time visualization of student locations during attendance
 */
export default function HeatmapDashboard({ sessionId }) {
    const [heatmapData, setHeatmapData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const deckRef = useRef(null);

    useEffect(() => {
        // Initial load
        const loadHeatmap = async () => {
            try {
                setLoading(true);
                const res = await getHeatmap(sessionId);
                setHeatmapData(res.data);
                setError(null);
            } catch (err) {
                console.error('Heatmap load error:', err);
                setError('Failed to load heatmap data');
            } finally {
                setLoading(false);
            }
        };

        if (sessionId) {
            loadHeatmap();
        }

        // Real-time subscription via WebSocket
        let unsubscribe = () => {};
        if (sessionId) {
            unsubscribe = subscribeToHeatmap(sessionId, (data) => {
                setHeatmapData(data);
            });
        }

        return unsubscribe;
    }, [sessionId]);

    if (loading) return <LoadingSpinner />;
    if (error) return <div className="p-4 text-red-600">{error}</div>;
    if (!heatmapData) return <div className="p-4">No data available</div>;

    const { points, geofenceCenter, geofenceRadius, totalStudents, clusterCount, insights } = heatmapData;

    // Create heatmap layer
    const layer = new HeatmapLayer({
        id: 'classroom-heatmap',
        data: points || [],
        getPosition: d => d, // [lng, lat]
        radiusPixels: 30,
        intensity: 1,
        threshold: 0.05,
        colorRange: [
            [255, 255, 178, 255],
            [254, 204, 92, 255],
            [253, 141, 60, 255],
            [240, 59, 32, 255],
            [189, 0, 38, 255]
        ]
    });

    return (
        <div className="p-6 max-w-7xl mx-auto">
            <PageHeader 
                title="Classroom Spatial Heatmap" 
                subtitle={`Session ${sessionId} - Real-time student distribution`}
            />

            {/* Stats Cards */}
            <div className="grid grid-cols-3 gap-4 mb-6">
                <div className="bg-white p-4 rounded-lg shadow">
                    <p className="text-gray-500 text-sm">Total Students</p>
                    <p className="text-3xl font-bold">{totalStudents || 0}</p>
                </div>
                <div className="bg-white p-4 rounded-lg shadow">
                    <p className="text-gray-500 text-sm">Clusters Detected</p>
                    <p className="text-3xl font-bold">{clusterCount || 0}</p>
                </div>
                <div className="bg-white p-4 rounded-lg shadow">
                    <p className="text-gray-500 text-sm">Geofence Radius</p>
                    <p className="text-3xl font-bold">{geofenceRadius || 100}m</p>
                </div>
            </div>

            {/* Heatmap Visualization */}
            <div className="bg-white rounded-lg shadow mb-6" style={{ height: '500px' }}>
                {points && points.length > 0 ? (
                    <DeckGL
                        ref={deckRef}
                        initialViewState={{
                            longitude: geofenceCenter?.[0] || 77.5946,
                            latitude: geofenceCenter?.[1] || 12.9716,
                            zoom: 17,
                            pitch: 0
                        }}
                        controller={true}
                        layers={[layer]}
                        style={{ width: '100%', height: '100%' }}
                    >
                        {/* Optional: Add map overlay here */}
                    </DeckGL>
                ) : (
                    <div className="h-full flex items-center justify-center text-gray-500">
                        No GPS data available for this session yet.
                    </div>
                )}
            </div>

            {/* AI Insights */}
            <div className="bg-white p-4 rounded-lg shadow">
                <h3 className="font-bold mb-2">AI Insights</h3>
                <pre className="whitespace-pre-wrap text-sm text-gray-700 font-sans">
                    {insights || 'No insights available'}
                </pre>
            </div>
        </div>
    );
};
