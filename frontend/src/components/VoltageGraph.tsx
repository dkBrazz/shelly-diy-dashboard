import React, { useMemo } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  ReferenceLine,
} from 'recharts';
import type { MeasureHistoryDTO } from '../types';
import { formatTime, formatDateTime } from '../utils/formatters';

interface VoltageGraphProps {
  data: MeasureHistoryDTO[];
  range: { start: number; end: number } | null;
}

const VoltageGraph: React.FC<VoltageGraphProps> = ({ data, range }) => {
  const chartData = useMemo(() => {
    return data.map(item => ({
      ...item,
      time: new Date(item.time).getTime()
    }));
  }, [data]);

  const domain = useMemo(() => {
    if (range) {
      return [range.start, range.end];
    }
    const end = Date.now();
    const start = end - 24 * 60 * 60 * 1000;
    return [start, end];
  }, [range]);

  if (!Array.isArray(data) || data.length === 0) {
    return (
      <div className="h-64 flex items-center justify-center bg-gray-900/50 rounded-lg border border-gray-800 border-dashed text-gray-600 text-sm">
        No historical data available
      </div>
    );
  }

  return (
    <div className="h-64 w-full mt-4">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={chartData}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#374151" />
          <XAxis 
            dataKey="time" 
            tickFormatter={(value) => formatTime(value)} 
            stroke="#9ca3af"
            fontSize={12}
            minTickGap={30}
            type="number"
            domain={domain}
            scale="time"
          />
          <YAxis 
            stroke="#9ca3af" 
            fontSize={12} 
            domain={[0, 280]}
            unit="V"
          />
          <ReferenceLine 
            y={230} 
            stroke="#4b5563" 
            strokeDasharray="3 3" 
            label={{ position: 'right', value: '230V', fill: '#9ca3af', fontSize: 10 }} 
          />
          <Tooltip
            contentStyle={{ backgroundColor: '#1f2937', border: 'none', borderRadius: '0.5rem', color: '#f3f4f6' }}
            itemStyle={{ color: '#f3f4f6' }}
            labelFormatter={(label) => formatDateTime(label)}
            formatter={(value: any) => [value != null ? value.toFixed(1) : '--', "V"]}
          />
          <Legend iconType="circle" />
          <Line
            type="linear"
            dataKey="aVoltage"
            name="Phase A"
            stroke="#ef4444"
            dot={false}
            strokeWidth={2}
            connectNulls
          />
          <Line
            type="linear"
            dataKey="bVoltage"
            name="Phase B"
            stroke="#f59e0b"
            dot={false}
            strokeWidth={2}
            connectNulls
          />
          <Line
            type="linear"
            dataKey="cVoltage"
            name="Phase C"
            stroke="#3b82f6"
            dot={false}
            strokeWidth={2}
            connectNulls
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
};

export default VoltageGraph;
