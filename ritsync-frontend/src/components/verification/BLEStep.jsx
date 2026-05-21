import { useEffect } from 'react';
import { useBLEScan } from '../../hooks/useBLEScan';
import Button from '../ui/Button';
import Spinner from '../ui/Spinner';

export default function BLEStep({ session, onComplete }) {
    const { startScan, isScanning, beaconResult } = useBLEScan(session.enableBle);

    useEffect(() => {
        if (beaconResult?.success) {
            onComplete({
                beaconUuid: beaconResult.uuid,
                beaconToken: beaconResult.token,
                beaconRssi: beaconResult.rssi
            });
        }
    }, [beaconResult]);

    if (!session.enableBle) {
        return (
            <div className="p-4 border rounded-lg bg-gray-50">
                <h3 className="font-bold mb-2">Bluetooth Beacon</h3>
                <p className="text-gray-600">BLE verification not required for this session.</p>
            </div>
        );
    }

    return (
        <div className="p-4 border rounded-lg space-y-4">
            <h3 className="font-bold">Bluetooth Beacon Verification</h3>
            <p className="text-sm text-gray-600">
                Scanning for classroom beacon to verify your physical presence...
            </p>
            <Button
                onClick={startScan}
                disabled={isScanning}
                className="w-full"
            >
                {isScanning ? <Spinner size="sm" /> : 'Scan for Beacon'}
            </Button>
            {beaconResult && (
                <div className={`p-3 rounded ${beaconResult.success ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                    {beaconResult.message}
                    {beaconResult.success && (
                        <div className="text-xs mt-1">
                            <div>UUID: {beaconResult.uuid}</div>
                            <div>Signal: {beaconResult.rssi} dBm</div>
                        </div>
                    )}
                </div>
            )}
            <div className="text-xs text-gray-500">
                Note: Requires Chrome/Edge browser. For other browsers, use the RIT-Sync mobile app.
            </div>
        </div>
    );
}
