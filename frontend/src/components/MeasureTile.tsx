import React from 'react';
import { formatMeasure } from '../utils/formatters';

interface MeasureTileProps {
  label: string;
  value: number | null | undefined;
  unit: string;
  phase?: 'A' | 'B' | 'C';
}

const MeasureTile: React.FC<MeasureTileProps> = ({ label, value, unit, phase }) => {
  const getPhaseColor = () => {
    switch (phase) {
      case 'A': return 'text-red-500';
      case 'B': return 'text-amber-500';
      case 'C': return 'text-blue-500';
      default: return 'text-gray-400';
    }
  };

  return (
    <div className="bg-gray-800 p-3 rounded-lg border border-gray-700">
      <div className="flex justify-between items-start mb-1">
        <span className="text-xs font-medium text-gray-400 uppercase tracking-wider">{label}</span>
        {phase && <span className={`text-xs font-bold ${getPhaseColor()}`}>L{phase}</span>}
      </div>
      <div className="flex items-baseline space-x-1">
        <span className="text-lg font-semibold text-gray-100">
          {formatMeasure(value)}
        </span>
        <span className="text-xs text-gray-500">{unit}</span>
      </div>
    </div>
  );
};

export default MeasureTile;
