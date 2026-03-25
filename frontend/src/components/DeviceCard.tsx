import React, { useEffect, useState } from 'react';
import type { Device, PowerMeasure, MeasureHistoryDTO, Scope } from '../types';
import MeasureTile from './MeasureTile';
import VoltageGraph from './VoltageGraph';
import CurrentGraph from './CurrentGraph';
import { Activity, Thermometer, Sigma } from 'lucide-react';
import { formatMeasure } from '../utils/formatters';
import { SCOPE_DURATIONS, SCOPE_LABELS } from '../types';

interface DeviceCardProps {
  device: Device;
  latestMeasure?: PowerMeasure;
  scope: Scope;
}

const DeviceCard: React.FC<DeviceCardProps> = ({ device, latestMeasure, scope }) => {
  const [history, setHistory] = useState<MeasureHistoryDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [historyRange, setHistoryRange] = useState<{ start: number; end: number } | null>(null);

  useEffect(() => {
    const fetchHistory = async () => {
      setLoading(true);
      try {
        const duration = SCOPE_DURATIONS[scope];
        const endTime = Date.now();
        const startTime = endTime - duration;
        const end = new Date(endTime).toISOString();
        const start = new Date(startTime).toISOString();
        const response = await fetch(`/api/devices/${device.id}/history?start=${start}&end=${end}`);
        if (!response.ok) {
           throw new Error(`Server returned ${response.status}`);
        }
        const data = await response.json();
        if (Array.isArray(data)) {
          setHistory(data);
          setHistoryRange({ start: startTime, end: endTime });
        } else {
          console.warn(`History data for device ${device.id} is not an array:`, data);
          setHistory([]);
        }
      } catch (error) {
        console.error(`Failed to fetch history for device ${device.id}:`, error);
      } finally {
        setLoading(false);
      }
    };

    fetchHistory();
    // Poll history every 10 minutes
    const interval = setInterval(fetchHistory, 10 * 60 * 1000);
    return () => clearInterval(interval);
  }, [device.id, scope]);

  const isOnline = latestMeasure && (Date.now() - new Date(latestMeasure.time).getTime() < 60000);

  return (
    <div className="min-w-[380px] bg-gray-900 border border-gray-800 rounded-xl overflow-hidden shadow-xl transition-all hover:border-gray-700">
      {/* Header */}
      <div className="bg-gray-800 p-4 border-b border-gray-700 flex justify-between items-center">
        <div className="flex items-center space-x-3">
          <div className={`p-2 rounded-lg ${isOnline ? 'bg-green-500/10' : 'bg-red-500/10'}`}>
            <Activity className={`w-5 h-5 ${isOnline ? 'text-green-500' : 'text-red-500'}`} />
          </div>
          <div>
            <h3 className="font-bold text-gray-100">{device.name || device.code || 'Shelly Device'}</h3>
            <p className="text-xs text-gray-500 font-mono">ID: {device.externalId}</p>
            <p className="text-xs text-gray-500 font-mono">Hex: {BigInt(device.externalId).toString(16).toLowerCase()}</p>
            <p className="text-xs text-gray-500 font-mono">Code: {device.code}</p>
            <p className="text-xs text-gray-400 font-mono">Gen: {device.gen || 'N/A'}</p>
          </div>
        </div>
        <div className="flex flex-col items-end space-y-2">
          <div className="flex items-center space-x-2 bg-gray-900/50 px-3 py-1 rounded-full border border-gray-700/50">
            <Thermometer className="w-4 h-4 text-orange-400" />
            <span className="text-sm font-semibold text-gray-200">
              {formatMeasure(latestMeasure?.temperature)}°C
            </span>
          </div>
          <div className="flex items-center space-x-2 bg-gray-900/50 px-3 py-2 rounded-full border border-gray-700/50">
            <Sigma className="w-4 h-4 text-orange-400" />
            <span className="text-sm font-semibold text-gray-200">{formatMeasure(latestMeasure?.totalPower!/1000)} kW</span>
          </div>
        </div>
      </div>

      <div className="p-4 space-y-4">
        {/* Real-time tiles - 3 columns for 3 phases */}
        <div className="grid grid-cols-3 gap-2">
          <MeasureTile label="Voltage" unit="V" value={latestMeasure?.aVoltage ?? null} phase="A" />
          <MeasureTile label="Voltage" unit="V" value={latestMeasure?.bVoltage ?? null} phase="B" />
          <MeasureTile label="Voltage" unit="V" value={latestMeasure?.cVoltage ?? null} phase="C" />
          
          <MeasureTile label="Current" unit="A" value={latestMeasure?.aCurrent ?? null} phase="A" />
          <MeasureTile label="Current" unit="A" value={latestMeasure?.bCurrent ?? null} phase="B" />
          <MeasureTile label="Current" unit="A" value={latestMeasure?.cCurrent ?? null} phase="C" />
          
          <MeasureTile label="Power" unit="kW" value={(latestMeasure?.aPower ?? 0)/1000} phase="A" />
          <MeasureTile label="Power" unit="kW" value={(latestMeasure?.bPower ?? 0)/1000} phase="B" />
          <MeasureTile label="Power" unit="kW" value={(latestMeasure?.cPower ?? 0)/1000} phase="C" />
        </div>

        {/* Total Power and Info */}
        {/*<div className="flex justify-between items-center bg-gray-800/50 p-3 rounded-lg border border-gray-700/50">*/}
        {/*   <div className="flex items-center space-x-2">*/}
        {/*     <Cpu className="w-4 h-4 text-blue-400" />*/}
        {/*     <span className="text-xs text-gray-400 font-medium">Gen: {device.gen || 'N/A'}</span>*/}
        {/*   </div>*/}
        {/*   <div className="text-right">*/}
        {/*     <span className="text-xs text-gray-500 block">Total Load</span>*/}
        {/*     <span className="text-xl font-bold text-gray-100">{formatMeasure(latestMeasure?.totalPower)} W</span>*/}
        {/*   </div>*/}
        {/*</div>*/}

        {/* Historical graph */}
        <div>
          <h4 className="text-sm font-semibold text-gray-400 flex items-center space-x-2 px-1">
            <span>{SCOPE_LABELS[scope]} Voltage History</span>
          </h4>
          {loading ? (
            <div className="h-64 flex items-center justify-center text-gray-500 text-sm">Loading history...</div>
          ) : (
            <VoltageGraph data={history} range={historyRange} />
          )}
        </div>

        <div>
          <h4 className="text-sm font-semibold text-gray-400 flex items-center space-x-2 px-1">
            <span>{SCOPE_LABELS[scope]} Current History</span>
          </h4>
          {loading ? (
            <div className="h-64 flex items-center justify-center text-gray-500 text-sm">Loading history...</div>
          ) : (
            <CurrentGraph data={history} range={historyRange} />
          )}
        </div>
      </div>
    </div>
  );
};

export default DeviceCard;
