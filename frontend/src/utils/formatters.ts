import { formatInTimeZone, fromZonedTime } from 'date-fns-tz';
import { parse } from 'date-fns';
import { APP_LOCALE, APP_DATE_LOCALE, APP_TIMEZONE } from '../config';

export const formatDateTime = (date: Date | string | number): string => {
  const d = new Date(date);
  return d.toLocaleString(APP_LOCALE, {
    timeZone: APP_TIMEZONE,
  });
};

export const formatTime = (date: Date | string | number, showSeconds = false): string => {
  const d = new Date(date);
  return d.toLocaleTimeString(APP_LOCALE, {
    timeZone: APP_TIMEZONE,
    hour: '2-digit',
    minute: '2-digit',
    ...(showSeconds && { second: '2-digit' }),
  });
};

export const formatMeasure = (value: number | null | undefined): string => {
  if (value === null || value === undefined) return '--';
  return value.toFixed(1);
};

export const formatToQuery = (date: Date | string | number): string => {
  const d = new Date(date);
  if (isNaN(d.getTime())) return '';
  return formatInTimeZone(d, APP_TIMEZONE, "P'T'p", { locale: APP_DATE_LOCALE });
};

export const parseFromQuery = (queryValue: string): string | null => {
  if (!queryValue) return null;
  try {
    const parsedDate = parse(queryValue, "P'T'p", new Date(), { locale: APP_DATE_LOCALE });
    if (isNaN(parsedDate.getTime())) return null;
    return fromZonedTime(parsedDate, APP_TIMEZONE).toISOString();
  } catch {
    return null;
  }
};
