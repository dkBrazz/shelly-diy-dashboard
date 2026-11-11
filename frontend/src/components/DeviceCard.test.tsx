import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import DeviceCard from './DeviceCard';
import type { Device, PowerMeasure } from '../types';

const NOW = new Date('2026-01-05T10:03:07.000Z');

const baseDevice: Device = {
  id: 7,
  externalId: 'a1b2c3',
  type: null,
  code: null,
  name: 'Garage meter',
  gen: null,
  createdAt: '2026-01-01T00:00:00.000Z',
};

const latestMeasure: PowerMeasure = {
  time: NOW.toISOString(),
  deviceId: 7,
  aVoltage: 231.2,
  aCurrent: 1.5,
  aPower: 345.6,
  bVoltage: 229.8,
  bCurrent: 0,
  bPower: 0,
  cVoltage: null,
  cCurrent: null,
  cPower: null,
  totalPower: 345.6,
  temperature: 41.3,
};

describe('DeviceCard', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } })),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders a hex external id without a Hex line', async () => {
    render(<DeviceCard device={baseDevice} latestMeasure={latestMeasure} now={NOW} scope="3h" />);

    expect(screen.getByRole('heading', { name: 'Garage meter' })).toBeInTheDocument();
    expect(screen.getByText('ID: a1b2c3')).toBeInTheDocument();
    expect(screen.queryByText(/^Hex:/)).not.toBeInTheDocument();
    expect(screen.getByText('Code: N/A')).toBeInTheDocument();
    expect(screen.getByText('Gen: N/A')).toBeInTheDocument();

    // History is fetched for the device and the empty result renders the placeholder.
    expect(await screen.findAllByText('No historical data available')).toHaveLength(2);
    expect(fetch).toHaveBeenCalledWith(expect.stringMatching(/^\/api\/devices\/7\/history\?start=.+&end=.+$/));
  });

  it('shows the zero-padded hex form for a decimal external id', async () => {
    const device: Device = { ...baseDevice, externalId: '3494547546478', code: 'SPEM-003CEBEU', gen: 'G2' };

    render(<DeviceCard device={device} latestMeasure={latestMeasure} now={NOW} scope="3h" />);

    expect(screen.getByText('ID: 3494547546478')).toBeInTheDocument();
    expect(screen.getByText('Hex: 032da343e16e')).toBeInTheDocument();
    expect(screen.getByText('Code: SPEM-003CEBEU')).toBeInTheDocument();
    expect(screen.getByText('Gen: G2')).toBeInTheDocument();

    await screen.findAllByText('No historical data available');
  });

  it('falls back to the device code and then a generic title when there is no name', async () => {
    const { unmount } = render(
      <DeviceCard device={{ ...baseDevice, name: null, code: 'SHEM-3' }} now={NOW} scope="3h" />,
    );
    expect(screen.getByRole('heading', { name: 'SHEM-3' })).toBeInTheDocument();
    await screen.findAllByText('No historical data available');
    unmount();

    render(<DeviceCard device={{ ...baseDevice, name: null, code: null }} now={NOW} scope="3h" />);
    expect(screen.getByRole('heading', { name: 'Shelly Device' })).toBeInTheDocument();
    await screen.findAllByText('No historical data available');
  });
});
