import { describe, expect, it } from 'vitest';
import {
  formatDateTime,
  formatDeviceIdHex,
  formatMeasure,
  formatTime,
  formatToQuery,
  parseFromQuery,
} from './formatters';

// 2026-01-05T10:03:07Z is 12:03:07 in Europe/Kyiv (UTC+2, winter time).
const SAMPLE_INSTANT = '2026-01-05T10:03:07.000Z';

describe('formatMeasure', () => {
  it('renders voltage, current and power readings with one decimal', () => {
    expect(formatMeasure(231.26)).toBe('231.3');
    expect(formatMeasure(4.5)).toBe('4.5');
    expect(formatMeasure(1234.56 / 1000)).toBe('1.2');
  });

  it('keeps zero as a real reading', () => {
    expect(formatMeasure(0)).toBe('0.0');
  });

  it('renders missing readings as a placeholder', () => {
    expect(formatMeasure(null)).toBe('--');
    expect(formatMeasure(undefined)).toBe('--');
  });
});

describe('formatTime', () => {
  it('renders hours and minutes in Europe/Kyiv', () => {
    expect(formatTime(SAMPLE_INSTANT)).toBe('12:03');
  });

  it('optionally includes seconds', () => {
    expect(formatTime(new Date(SAMPLE_INSTANT), true)).toBe('12:03:07');
  });

  it('accepts epoch milliseconds', () => {
    expect(formatTime(new Date(SAMPLE_INSTANT).getTime())).toBe('12:03');
  });
});

describe('formatDateTime', () => {
  it('renders a uk-UA date and time in Europe/Kyiv', () => {
    expect(formatDateTime(SAMPLE_INSTANT)).toBe('05.01.2026, 12:03:07');
  });
});

describe('formatToQuery / parseFromQuery', () => {
  it('encodes an instant as a Kyiv-local query value', () => {
    expect(formatToQuery(SAMPLE_INSTANT)).toBe('05.01.2026T12:03');
  });

  it('returns an empty string for an invalid date', () => {
    expect(formatToQuery('not a date')).toBe('');
  });

  it('round-trips a query value back to the same instant (to the minute)', () => {
    expect(parseFromQuery(formatToQuery(SAMPLE_INSTANT))).toBe('2026-01-05T10:03:00.000Z');
  });

  it('returns null for empty or unparsable query values', () => {
    expect(parseFromQuery('')).toBeNull();
    expect(parseFromQuery('garbage')).toBeNull();
  });
});

describe('formatDeviceIdHex', () => {
  it('converts a decimal id to zero-padded 6-char hex', () => {
    expect(formatDeviceIdHex('123456')).toBe('01e240');
    expect(formatDeviceIdHex('16777215')).toBe('ffffff');
  });

  it('converts a large decimal id to zero-padded 12-char hex', () => {
    expect(formatDeviceIdHex('16777216')).toBe('000001000000');
    expect(formatDeviceIdHex('3494547546478')).toBe('032da343e16e');
  });

  it('returns null for a Gen1 hex id', () => {
    expect(formatDeviceIdHex('a1b2c3')).toBeNull();
  });

  it('returns null for BLE / Z-Wave ids starting with X', () => {
    expect(formatDeviceIdHex('X1234567890ab')).toBeNull();
  });

  it('returns null for an empty id', () => {
    expect(formatDeviceIdHex('')).toBeNull();
  });
});
