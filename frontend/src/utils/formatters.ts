export const KYIV_LOCALE = 'uk-UA';
export const KYIV_TIMEZONE = 'Europe/Kyiv';

export const formatDateTime = (date: Date | string | number): string => {
  const d = new Date(date);
  return d.toLocaleString(KYIV_LOCALE, {
    timeZone: KYIV_TIMEZONE,
  });
};

export const formatTime = (date: Date | string | number, showSeconds = false): string => {
  const d = new Date(date);
  return d.toLocaleTimeString(KYIV_LOCALE, {
    timeZone: KYIV_TIMEZONE,
    hour: '2-digit',
    minute: '2-digit',
    ...(showSeconds && { second: '2-digit' }),
  });
};

export const formatMeasure = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return '--';
  return value.toFixed(1);
};
