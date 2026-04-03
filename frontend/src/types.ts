export interface Device {
  id: number;
  externalId: string;
  type: string;
  code: string;
  name?: string;
  gen: string;
  createdAt: string;
}

export interface PowerMeasure {
  time: string;
  deviceId: number;
  aVoltage: number | null;
  aCurrent: number | null;
  aPower: number | null;
  bVoltage: number | null;
  bCurrent: number | null;
  bPower: number | null;
  cVoltage: number | null;
  cCurrent: number | null;
  cPower: number | null;
  totalPower: number | null;
  temperature: number | null;
}

export interface MeasureHistoryDTO {
  time: string;
  aVoltage: number | null;
  aCurrent: number | null;
  aPower: number | null;
  bVoltage: number | null;
  bCurrent: number | null;
  bPower: number | null;
  cVoltage: number | null;
  cCurrent: number | null;
  cPower: number | null;
  totalPower: number | null;
  temperature: number | null;
}


export interface UserInfo {
  email: string | null;
  name: string | null;
  picture: string | null;
}

export type Scope = '5m' | '30m' | '1h' | '3h' | '12h' | '24h' | '3d' | '1w' | 'custom';

export const SCOPE_LABELS: Record<Scope, string> = {
  '5m': '5 minutes',
  '30m': '30 minutes',
  '1h': '1 hour',
  '3h': '3 hours',
  '12h': '12 hours',
  '24h': '24 hours',
  '3d': '3 days',
  '1w': '1 week',
  'custom': 'Custom Range',
};

export const SCOPE_DURATIONS: Record<string, number> = {
  '5m': 5 * 60 * 1000,
  '30m': 30 * 60 * 1000,
  '1h': 60 * 60 * 1000,
  '3h': 3 * 60 * 60 * 1000,
  '12h': 12 * 60 * 60 * 1000,
  '24h': 24 * 60 * 60 * 1000,
  '3d': 3 * 24 * 60 * 60 * 1000,
  '1w': 7 * 24 * 60 * 60 * 1000,
};
