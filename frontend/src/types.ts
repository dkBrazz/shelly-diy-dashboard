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
