import React from 'react';
import type { Scope } from '../types';
import { Clock } from 'lucide-react';

interface ScopeSwitcherProps {
  currentScope: Scope;
  onScopeChange: (scope: Scope) => void;
}

const ScopeSwitcher: React.FC<ScopeSwitcherProps> = ({ currentScope, onScopeChange }) => {
  const scopes: Scope[] = ['5m', '30m', '1h', '3h', '12h', '24h', '1w'];

  return (
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
  );
};

export default ScopeSwitcher;
