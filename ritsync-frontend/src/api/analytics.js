import apiClient from './client';

/**
 * Get heatmap data for a session
 * @param {number} sessionId 
 * @returns {Promise<object>} heatmap payload
 */
export const getHeatmap = async (sessionId) => {
    const res = await apiClient.get(`/analytics/heatmap/${sessionId}`);
    return res.data.data;
};

/**
 * Subscribe to real-time heatmap updates via WebSocket
 * @param {number} sessionId 
 * @param {Function} callback - Called with new heatmap data
 * @returns {Function} Unsubscribe function
 */
export const subscribeToHeatmap = (sessionId, callback) => {
    // Dynamic import to avoid SSR issues
    import('sockjs-client').then((SockJS) => {
        import('@stomp/stompjs').then(({ Client }) => {
            const socket = new SockJS('http://localhost:8080/api/ws-heatmap');
            const stompClient = Client;
            
            stompClient.onConnect = () => {
                stompClient.subscribe(`/topic/session/${sessionId}/heatmap`, (msg) => {
                    try {
                        const data = JSON.parse(msg.body);
                        callback(data);
                    } catch (e) {
                        console.error('Heatmap WebSocket parse error:', e);
                    }
                });
            };

            stompClient.onStompError = (frame) => {
                console.error('Heatmap STOMP error:', frame);
            };

            stompClient.webSocketFactory = () => socket;
            stompClient.activate();

            // Return cleanup function
            return () => {
                if (stompClient.active) {
                    stompClient.deactivate();
                }
            };
        });
    });

    // Fallback: Return noop cleanup
    return () => {};
};
