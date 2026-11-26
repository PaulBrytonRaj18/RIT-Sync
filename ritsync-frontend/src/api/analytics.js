import { Client } from '@stomp/stompjs';
import apiClient from './client';
import useAuthStore from '../store/authStore';

/**
 * Get heatmap data for a session
 * @param {number} sessionId
 * @returns {Promise<object>} heatmap payload
 */
export const getHeatmap = async (sessionId) => {
    const res = await apiClient.get(`/analytics/heatmap/${sessionId}`);
    return res.data.data;
};

// Same origin as the app; in dev, Vite proxies /api (including WebSockets) to the backend.
const wsUrl = () => {
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${window.location.host}/api/ws-heatmap/websocket`;
};

/**
 * Subscribe to real-time heatmap updates (pushed after each successful check-in).
 * Only the session's instructor or an admin is allowed to subscribe.
 * @param {number} sessionId
 * @param {(data: object) => void} callback - called with each new heatmap payload
 * @returns {() => void} unsubscribe / disconnect
 */
export const subscribeToHeatmap = (sessionId, callback) => {
    const client = new Client({
        brokerURL: wsUrl(),
        reconnectDelay: 5000,
        // Read the token on every (re)connect so a refreshed token is used
        beforeConnect: () => {
            const token = useAuthStore.getState().accessToken;
            client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
        },
        onConnect: () => {
            client.subscribe(`/topic/session/${sessionId}/heatmap`, (msg) => {
                try {
                    callback(JSON.parse(msg.body));
                } catch (e) {
                    console.error('Heatmap WebSocket parse error:', e);
                }
            });
        },
        onStompError: (frame) => {
            console.warn('Heatmap live updates unavailable:', frame.headers?.message);
        },
    });

    client.activate();
    return () => { client.deactivate(); };
};
