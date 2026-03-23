import { useEffect, useState } from 'react';
import type { Device, PowerMeasure, UserInfo, Scope } from './types';
import DeviceCard from './components/DeviceCard';
import ScopeSwitcher from './components/ScopeSwitcher';
import ErrorBoundary from './components/ErrorBoundary';
import { LayoutDashboard, RefreshCw, Zap, AlertCircle, LogOut, LogIn, User } from 'lucide-react';
import { formatTime } from './utils/formatters';
import { SCOPE_DURATIONS } from './types';

function App() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [latestMeasures, setLatestMeasures] = useState<Record<number, PowerMeasure>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdate, setLastUpdate] = useState<Date>(new Date());
  const [user, setUser] = useState<UserInfo | null>(null);
  const [scope, setScope] = useState<Scope>(() => {
    const searchParams = new URLSearchParams(window.location.search);
    const s = searchParams.get('scope') as Scope;
    if (s && SCOPE_DURATIONS[s]) return s;
    const stored = localStorage.getItem('selectedScope') as Scope;
    if (stored && SCOPE_DURATIONS[stored]) return stored;
    return '3h';
  });

  useEffect(() => {
    const searchParams = new URLSearchParams(window.location.search);
    searchParams.set('scope', scope);
    const newUrl = `${window.location.pathname}?${searchParams.toString()}`;
    window.history.replaceState(null, '', newUrl);
    localStorage.setItem('selectedScope', scope);
  }, [scope]);

  const fetchUser = async (): Promise<UserInfo | null> => {
    try {
      const res = await fetch('/api/user/me');
      if (res.ok) {
        const userData = await res.json();
        setUser(userData);
        return userData;
      } else {
        setUser(null);
        return null;
      }
    } catch (err) {
      console.error('Failed to fetch user:', err);
      setUser(null);
      return null;
    }
  };

  const fetchData = async () => {
    try {
      const [devicesRes, latestRes] = await Promise.all([
        fetch('/api/devices'),
        fetch('/api/devices/latest')
      ]);
      
      if (devicesRes.status === 401 || latestRes.status === 401) {
        setError('Unauthorized. Please log in.');
        setUser(null);
        return;
      }
      
      if (!devicesRes.ok || !latestRes.ok) {
        throw new Error('Failed to fetch data from server');
      }

      const devicesData = await devicesRes.json();
      const latestData = await latestRes.json();
      
      if (Array.isArray(devicesData)) {
        setDevices(devicesData);
      }
      
      const latestMap: Record<number, PowerMeasure> = {};
      if (Array.isArray(latestData)) {
        latestData.forEach((m: any) => {
          const deviceId = m.deviceId ?? m.device?.id;
          if (deviceId !== undefined) {
            latestMap[deviceId] = m;
          }
        });
      }
      setLatestMeasures(latestMap);
      setLastUpdate(new Date());
      setError(null);
    } catch (err: any) {
      console.error('Failed to fetch data:', err);
      setError(err.message || 'Connection error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUser().finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!user) return;

    fetchData();
    
    // Poll latest measures every 15 seconds
    const interval = setInterval(fetchData, 15 * 1000);
    
    return () => clearInterval(interval);
  }, [user?.email]);

  return (
    <div className="min-h-screen w-full bg-black text-gray-100 font-sans p-4 md:p-8">
      {/* Navbar */}
      <header className="w-full flex flex-col space-y-6 mb-8">
        <div className="flex flex-col md:flex-row justify-between items-center gap-4">
          <div className="flex items-center space-x-3">
            <div className="bg-blue-600 p-2 rounded-lg shadow-lg shadow-blue-600/20">
              <Zap className="w-6 h-6 text-white" />
            </div>
            <div>
              <h1 className="text-2xl font-black tracking-tight uppercase italic">Shelly <span className="text-blue-500">Cloud</span></h1>
              <div className="flex items-center space-x-2">
                <span className="flex h-2 w-2 rounded-full bg-green-500 animate-pulse"></span>
                <span className="text-[10px] text-gray-500 font-bold uppercase tracking-widest">Real-time Monitoring</span>
              </div>
            </div>
          </div>

          <div className="flex items-center space-x-6">
             {user ? (
               <div className="flex items-center space-x-4 pr-6 border-r border-gray-800">
                 {user.picture ? (
                   <img src={user.picture} alt={user.name || 'User'} className="w-8 h-8 rounded-full border border-gray-700" />
                 ) : (
                   <div className="w-8 h-8 rounded-full bg-gray-800 flex items-center justify-center">
                     <User className="w-4 h-4 text-gray-400" />
                   </div>
                 )}
                 <div className="text-right hidden sm:block">
                   <span className="text-[10px] text-gray-500 block uppercase font-bold">Logged in as</span>
                   <span className="text-xs font-bold text-gray-300">{user.name || user.email}</span>
                 </div>
                 <a 
                   href="/logout" 
                   className="p-2 hover:bg-gray-800 rounded-lg transition-colors group text-gray-400 hover:text-red-400"
                   title="Logout"
                 >
                   <LogOut className="w-5 h-5" />
                 </a>
               </div>
             ) : (
               <a 
                 href="/oauth2/authorization/google" 
                 className="flex items-center space-x-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg font-bold text-sm transition-colors shadow-lg shadow-blue-600/20"
               >
                 <LogIn className="w-4 h-4" />
                 <span>Login with Google</span>
               </a>
             )}
             
             <div className="text-right">
               <span className="text-[10px] text-gray-500 block uppercase font-bold">Last Update</span>
               <span className="text-xs font-mono text-gray-400">{formatTime(lastUpdate, true)}</span>
             </div>
             <button 
               onClick={() => { setLoading(true); fetchData(); }}
               className="p-2 hover:bg-gray-800 rounded-full transition-colors group"
             >
               <RefreshCw className={`w-5 h-5 text-gray-400 group-hover:text-blue-400 ${loading ? 'animate-spin' : ''}`} />
             </button>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row justify-between items-center gap-4 bg-gray-900/30 p-4 rounded-xl border border-gray-800/50">
          <div className="flex items-center space-x-2">
            <LayoutDashboard className="w-4 h-4 text-blue-500" />
            <h2 className="text-sm font-bold uppercase tracking-wider text-gray-400">Device Dashboard</h2>
          </div>
          <ScopeSwitcher currentScope={scope} onScopeChange={setScope} />
        </div>
      </header>

      {/* Main Content */}
      <main className="w-full">
        {error && devices.length === 0 && (
          <div className="bg-red-900/20 border border-red-500/50 rounded-xl p-4 mb-6 flex items-center space-x-3 text-red-200">
            <AlertCircle className="w-5 h-5" />
            <p className="text-sm font-medium">{error}</p>
          </div>
        )}

        {loading && devices.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-64 space-y-4">
            <div className="w-12 h-12 border-4 border-blue-600 border-t-transparent rounded-full animate-spin"></div>
            <p className="text-gray-500 font-medium">Initializing Dashboard...</p>
          </div>
        ) : (
          <div className="grid grid-cols-[repeat(auto-fill,minmax(380px,1fr))] gap-6 md:gap-8">
            {devices.map(device => (
              <ErrorBoundary key={device.id}>
                <DeviceCard 
                  device={device} 
                  latestMeasure={latestMeasures[device.id]} 
                  scope={scope}
                />
              </ErrorBoundary>
            ))}
            
            {devices.length === 0 && !loading && (
              <div className="col-span-full bg-gray-900/50 border-2 border-dashed border-gray-800 rounded-2xl p-12 text-center">
                <LayoutDashboard className="w-12 h-12 text-gray-700 mx-auto mb-4" />
                <h3 className="text-lg font-bold text-gray-400">No Devices Found</h3>
                <p className="text-gray-600 max-w-sm mx-auto mt-2">
                  Waiting for events from Shelly Cloud... Connect your devices to see them appear here.
                </p>
              </div>
            )}
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="w-full mt-12 pt-8 border-t border-gray-900 text-center">
        <p className="text-xs text-gray-600 font-medium uppercase tracking-widest flex items-center justify-center space-x-2">
          <span>Shelly Dashboard DIY</span>
          <span className="h-1 w-1 bg-gray-800 rounded-full"></span>
          <span>JVM Stack + Kotlin + React</span>
        </p>
      </footer>
    </div>
  );
}

export default App;
