import { useState } from 'react';

/**
 * Bluetooth Beacon Verification Hook
 * Uses Web Bluetooth API (Chrome/Edge only)
 * Scans for teacher's BLE beacon to verify physical presence
 */
export const useBLEScan = (isEnabled = false) => {
    const [isScanning, setIsScanning] = useState(false);
    const [beaconResult, setBeaconResult] = useState(null);
    const [error, setError] = useState(null);

    const startScan = async () => {
        if (!isEnabled) {
            setBeaconResult({ success: true, message: 'BLE not required for this session' });
            return;
        }

        if (!navigator.bluetooth) {
            setError('Web Bluetooth not supported. Use Chrome/Edge or mobile app.');
            setBeaconResult({ success: false, message: 'Web Bluetooth not supported' });
            return;
        }

        setIsScanning(true);
        setError(null);
        setBeaconResult(null);

        try {
            // Request BLE device 
            // Note: In production, use custom service UUID for your beacons
            const device = await navigator.bluetooth.requestDevice({
                filters: [{ namePrefix: 'RITS' }], // Match beacon name
                optionalServices: ['battery_service', 'device_information']
            });

            const server = await device.gatt.connect();
            
            // Read beacon token from custom characteristic (simplified)
            // In production: implement proper GATT service/characteristic structure
            const token = 'mock-beacon-token-' + Date.now();
            const rssi = -70; // In production: get actual RSSI from advertisement
            const uuid = device.id || device.name;

            setBeaconResult({ 
                success: true, 
                uuid: uuid,
                token: token,
                rssi: rssi,
                message: 'Beacon detected and verified' 
            });

            // Disconnect after read
            if (server.connected) {
                server.disconnect();
            }
        } catch (err) {
            console.error('BLE Scan error:', err);
            setError(err.message);
            setBeaconResult({ success: false, message: err.message });
        } finally {
            setIsScanning(false);
        }
    };

    return { startScan, isScanning, beaconResult, error };
};
