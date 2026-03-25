import React from 'react';
import type { Scope } from '../types';
import { Clock, Calendar } from 'lucide-react';
import { formatInTimeZone, fromZonedTime } from 'date-fns-tz';

import { APP_TIMEZONE } from '../config';

interface ScopeSwitcherProps {
  currentScope: Scope;
  onScopeChange: (scope: Scope) => void;
  customRange?: { start: string; end: string };
  onCustomRangeChange?: (range: { start: string; end: string }) => void;
}

const ScopeSwitcher: React.FC<ScopeSwitcherProps> = ({ 
  currentScope, 
  onScopeChange,
  customRange,
  onCustomRangeChange
}) => {
  const scopes: Scope[] = ['5m', '30m', '1h', '3h', '12h', '24h', '1w', 'custom'];

  // Helper to convert ISO to datetime-local input format (YYYY-MM-DDTHH:mm) in APP_TIMEZONE
  const toAppTZISO = (isoString: string) => {
    try {
      const date = new Date(isoString);
      if (isNaN(date.getTime())) return '';
      return formatInTimeZone(date, APP_TIMEZONE, "yyyy-MM-dd'T'HH:mm");
    } catch {
      return '';
    }
  };

  const handleCustomDateChange = (type: 'start' | 'end', value: string) => {
    if (!customRange || !onCustomRangeChange || !value) return;
    
    try {
      const finalDate = fromZonedTime(value, APP_TIMEZONE);
      
      if (!isNaN(finalDate.getTime())) {
        onCustomRangeChange({
          ...customRange,
          [type]: finalDate.toISOString()
        });
      }
    } catch (e) {
      console.error('Failed to parse date:', e);
    }
  };

  return (
    <div className="flex flex-col md:flex-row items-center gap-2">
      <div className="flex items-center space-x-2 bg-gray-900/50 p-1 rounded-lg border border-gray-800">
        <div className="flex items-center px-2 text-gray-400 border-r border-gray-800">
          <Clock className="w-3.5 h-3.5 mr-1.5" />
          <span className="text-[10px] font-bold uppercase tracking-wider">Scope</span>
        </div>
        <div className="flex space-x-0.5">
          {scopes.map((s) => (
            <button
              key={s}
              onClick={() => onScopeChange(s)}
              className={`px-2 py-1 text-[10px] font-bold rounded uppercase transition-all ${
                currentScope === s
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'text-gray-500 hover:text-gray-300 hover:bg-gray-800'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {currentScope === 'custom' && customRange && (
        <div className="flex items-center space-x-2 bg-gray-900/50 p-1 rounded-lg border border-gray-800">
          <div className="flex items-center px-2 text-gray-400 border-r border-gray-800">
            <Calendar className="w-3.5 h-3.5 mr-1.5" />
            <span className="text-[10px] font-bold uppercase tracking-wider">Range ({APP_TIMEZONE})</span>
          </div>
          <div className="flex items-center space-x-2 px-1">
             <input 
               type="datetime-local" 
               value={toAppTZISO(customRange.start)}
               onChange={(e) => handleCustomDateChange('start', e.target.value)}
               className="bg-gray-800 text-gray-200 text-[10px] border border-gray-700 rounded px-1 py-0.5 outline-none focus:border-blue-500"
             />
             <span className="text-gray-600 text-[10px]">-</span>
             <input 
               type="datetime-local" 
               value={toAppTZISO(customRange.end)}
               onChange={(e) => handleCustomDateChange('end', e.target.value)}
               className="bg-gray-800 text-gray-200 text-[10px] border border-gray-700 rounded px-1 py-0.5 outline-none focus:border-blue-500"
             />
          </div>
        </div>
      )}
    </div>
  );
};

export default ScopeSwitcher;
