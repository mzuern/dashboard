import type { CameraResolutionPreset } from '../../types/domain';

export const CAMERA_RESOLUTION_PRESETS: CameraResolutionPreset[] = [
  { label: 'Standard (1280x720)', width: 1280, height: 720 },
  { label: 'High (1920x1080)', width: 1920, height: 1080 },
  { label: 'Maximum (2560x1440)', width: 2560, height: 1440 },
];

export class CameraManager {
  private stream: MediaStream | null = null;
  readonly video: HTMLVideoElement;

  constructor() {
    this.video = document.createElement('video');
    this.video.setAttribute('playsinline', 'true');
    this.video.muted = true;
    this.video.autoplay = true;
  }

  async start(resolution: CameraResolutionPreset, deviceId?: string): Promise<void> {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('Camera access is not supported in this browser.');
    }
    this.stop();
    const videoConstraints: MediaTrackConstraints = deviceId
      ? { deviceId: { exact: deviceId }, width: { ideal: resolution.width }, height: { ideal: resolution.height } }
      : {
          facingMode: { ideal: 'environment' },
          width: { ideal: resolution.width },
          height: { ideal: resolution.height },
        };

    const stream = await navigator.mediaDevices.getUserMedia({
      video: videoConstraints,
      audio: false,
    });
    this.stream = stream;
    this.video.srcObject = stream;
    await this.video.play();
    await this.waitForDimensions();
  }

  private waitForDimensions(): Promise<void> {
    if (this.video.videoWidth > 0) return Promise.resolve();
    return new Promise((resolve) => {
      const onLoaded = () => {
        this.video.removeEventListener('loadedmetadata', onLoaded);
        resolve();
      };
      this.video.addEventListener('loadedmetadata', onLoaded);
    });
  }

  stop(): void {
    if (this.stream) {
      this.stream.getTracks().forEach((track) => track.stop());
      this.stream = null;
    }
  }

  get isActive(): boolean {
    return this.stream !== null;
  }

  get width(): number {
    return this.video.videoWidth;
  }

  get height(): number {
    return this.video.videoHeight;
  }

  async listCameras(): Promise<MediaDeviceInfo[]> {
    const devices = await navigator.mediaDevices.enumerateDevices();
    return devices.filter((d) => d.kind === 'videoinput');
  }

  /** Draws the current video frame into the given canvas at its native resolution. */
  grabFrame(canvas: HTMLCanvasElement): CanvasRenderingContext2D | null {
    if (!this.video.videoWidth) return null;
    canvas.width = this.video.videoWidth;
    canvas.height = this.video.videoHeight;
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;
    ctx.drawImage(this.video, 0, 0, canvas.width, canvas.height);
    return ctx;
  }
}
