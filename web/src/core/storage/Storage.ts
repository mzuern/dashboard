import { openDB, type IDBPDatabase } from 'idb';
import type { AppSettings, ReviewRow } from '../../types/domain';
import { DEFAULT_BOARD_TEMPLATE } from '../board/BoardTemplate';
import { CAMERA_RESOLUTION_PRESETS } from '../camera/CameraManager';

const DB_NAME = 'production-scanner';
const DB_VERSION = 1;
const STORE = 'kv';

export const DEFAULT_SETTINGS: AppSettings = {
  boardTemplate: DEFAULT_BOARD_TEMPLATE,
  ocrConfidenceThreshold: 65,
  coverageThreshold: 0.9,
  cameraResolution: CAMERA_RESOLUTION_PRESETS[1],
  debugMode: false,
};

let dbPromise: Promise<IDBPDatabase> | null = null;

function getDb(): Promise<IDBPDatabase> {
  if (!dbPromise) {
    dbPromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(db) {
        if (!db.objectStoreNames.contains(STORE)) {
          db.createObjectStore(STORE);
        }
      },
    });
  }
  return dbPromise;
}

/**
 * Thin persistence layer over IndexedDB for settings, the board template,
 * and an in-progress review draft (so a refresh mid-review doesn't lose
 * scanned data). Works fully offline since IndexedDB is local to the
 * browser.
 */
export const Storage = {
  async getSettings(): Promise<AppSettings> {
    const db = await getDb();
    const stored = (await db.get(STORE, 'settings')) as AppSettings | undefined;
    if (!stored) return DEFAULT_SETTINGS;
    return { ...DEFAULT_SETTINGS, ...stored, boardTemplate: { ...DEFAULT_SETTINGS.boardTemplate, ...stored.boardTemplate } };
  },

  async setSettings(settings: AppSettings): Promise<void> {
    const db = await getDb();
    await db.put(STORE, settings, 'settings');
  },

  async getDraftRows(): Promise<ReviewRow[] | undefined> {
    const db = await getDb();
    return db.get(STORE, 'draftRows');
  },

  async setDraftRows(rows: ReviewRow[]): Promise<void> {
    const db = await getDb();
    await db.put(STORE, rows, 'draftRows');
  },

  async clearDraftRows(): Promise<void> {
    const db = await getDb();
    await db.delete(STORE, 'draftRows');
  },
};
