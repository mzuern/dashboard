import { useEffect, useState } from 'react';
import type { AppSettings, ReviewRow } from './types/domain';
import { Settings } from './core/settings/Settings';
import { Storage } from './core/storage/Storage';
import { ScanPipeline } from './state/ScanPipeline';
import { ScanScreen } from './ui/screens/ScanScreen';
import { ReviewScreen } from './ui/screens/ReviewScreen';
import { EmailScreen } from './ui/screens/EmailScreen';
import { SettingsScreen } from './ui/screens/SettingsScreen';

type View = 'home' | 'scan' | 'review' | 'email' | 'settings';

/** Loads persisted settings/draft rows before mounting the real app, so AppShell can construct its ScanPipeline once, synchronously, from known-good initial data. */
export default function App() {
  const [bootstrap, setBootstrap] = useState<{ settings: AppSettings; draftRows: ReviewRow[] } | null>(null);

  useEffect(() => {
    void (async () => {
      const [settings, draftRows] = await Promise.all([Settings.load(), Storage.getDraftRows()]);
      setBootstrap({ settings, draftRows: draftRows ?? [] });
    })();
  }, []);

  if (!bootstrap) {
    return (
      <div className="app-shell app-shell--loading">
        <p>Loading...</p>
      </div>
    );
  }

  return <AppShell initialSettings={bootstrap.settings} initialReviewRows={bootstrap.draftRows} />;
}

function AppShell({ initialSettings, initialReviewRows }: { initialSettings: AppSettings; initialReviewRows: ReviewRow[] }) {
  const [pipeline] = useState(() => new ScanPipeline(initialSettings));
  const [settings, setSettings] = useState(initialSettings);
  const [view, setView] = useState<View>(initialReviewRows.length > 0 ? 'review' : 'home');
  const [reviewRows, setReviewRows] = useState<ReviewRow[]>(initialReviewRows);

  useEffect(() => {
    pipeline.updateSettings(settings);
  }, [pipeline, settings]);

  useEffect(() => {
    return () => pipeline.dispose();
  }, [pipeline]);

  useEffect(() => {
    void Storage.setDraftRows(reviewRows);
  }, [reviewRows]);

  function handleScanComplete(rows: ReviewRow[]) {
    setReviewRows(rows);
    setView('review');
  }

  function handleStartOver() {
    pipeline.cancelScan();
    setReviewRows([]);
    void Storage.clearDraftRows();
    setView('home');
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <span className="app-header__title">Production Board Scanner</span>
        {view !== 'settings' && (
          <button className="app-header__settings" onClick={() => setView('settings')} aria-label="Settings">
            ⚙
          </button>
        )}
      </header>

      <main className="app-main">
        {view === 'home' && (
          <div className="home-screen">
            <p>Scan the production whiteboard to generate today's status email.</p>
            <button className="btn btn--primary btn--large" onClick={() => setView('scan')}>
              Scan Board
            </button>
          </div>
        )}

        {view === 'scan' && (
          <ScanScreen pipeline={pipeline} settings={settings} onComplete={handleScanComplete} onCancel={() => setView('home')} />
        )}

        {view === 'review' && (
          <ReviewScreen
            rows={reviewRows}
            confidenceThreshold={settings.ocrConfidenceThreshold}
            onChange={setReviewRows}
            onProceed={() => setView('email')}
            onBack={() => setView('scan')}
          />
        )}

        {view === 'email' && <EmailScreen rows={reviewRows} onBack={() => setView('review')} onStartOver={handleStartOver} />}

        {view === 'settings' && (
          <SettingsScreen
            settings={settings}
            onSaved={(s) => setSettings(s)}
            onBack={() => setView(reviewRows.length > 0 ? 'review' : 'home')}
          />
        )}
      </main>
    </div>
  );
}
