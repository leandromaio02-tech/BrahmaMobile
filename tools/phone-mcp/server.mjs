#!/usr/bin/env node
/**
 * Brahma Phone MCP server.
 *
 * Gives an MCP client eyes and hands on the Android device that this process can
 * reach over adb. Everything runs as the adb shell user, so input injection,
 * screenshots and UI dumps are permitted (unlike calling /system/bin/input from
 * an unprivileged app uid).
 *
 * Prerequisite (one time): enable Developer options -> Wireless debugging on the
 * phone, then `adb pair 127.0.0.1:<port> <code>` and `adb connect 127.0.0.1:<port>`.
 * Set PHONE_ADB_SERIAL to pin a device when more than one is attached.
 */
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';

const exec = promisify(execFile);
const ADB = process.env.PHONE_ADB_BIN || 'adb';

async function adb(args, { timeout = 30_000, maxBuffer = 64 * 1024 * 1024 } = {}) {
  const { stdout } = await exec(ADB, args, { timeout, maxBuffer, encoding: 'utf8' });
  return stdout;
}

async function adbBytes(args, opts = {}) {
  const { stdout } = await exec(ADB, args, { ...opts, encoding: 'buffer' });
  return stdout;
}

function serialArgs() {
  const s = process.env.PHONE_ADB_SERIAL?.trim();
  return s ? ['-s', s] : [];
}

/** Runs an adb command against the selected device. Throws a clear error if none. */
async function deviceAdb(args, opts) {
  const out = await adb([...serialArgs(), ...args], opts);
  return out;
}

async function requireDevice() {
  const list = await adb(['devices']);
  const lines = list.split('\n').slice(1).map((l) => l.trim()).filter(Boolean);
  const ready = lines.filter((l) => l.endsWith('device'));
  if (!ready.length) {
    throw new Error(
      'No authorized adb device. Enable Wireless debugging on the phone, then run:\n' +
        '  adb pair 127.0.0.1:<pair-port> <code>\n' +
        '  adb connect 127.0.0.1:<connect-port>'
    );
  }
  if (ready.length > 1 && !process.env.PHONE_ADB_SERIAL) {
    throw new Error(`Multiple devices (${ready.map((l) => l.split('\t')[0]).join(', ')}); set PHONE_ADB_SERIAL.`);
  }
  return ready[0].split('\t')[0];
}

function textResult(text) {
  return { content: [{ type: 'text', text }] };
}
function errorResult(message) {
  return { content: [{ type: 'text', text: String(message) }], isError: true };
}

/* ------------------------------------------------------------------ tools */

async function getStatus() {
  await requireDevice();
  const [dev, model, release, sdk, battery] = await Promise.all([
    deviceAdb(['shell', 'getprop', 'ro.product.model']).catch(() => '?'),
    deviceAdb(['shell', 'getprop', 'ro.product.model']).catch(() => '?'),
    deviceAdb(['shell', 'getprop', 'ro.build.version.release']).catch(() => '?'),
    deviceAdb(['shell', 'getprop', 'ro.build.version.sdk']).catch(() => '?'),
    deviceAdb(['shell', 'dumpsys', 'battery']).catch(() => ''),
  ]);
  const level = /level:\s*(\d+)/.exec(battery)?.[1] ?? '?';
  return textResult(
    [
      `device: ${dev.trim()}`,
      `model: ${model.trim()} | android ${release.trim()} (sdk ${sdk.trim()})`,
      `battery: ${level}%`,
    ].join('\n')
  );
}

async function capture() {
  await requireDevice();
  let png = await adbBytes([...serialArgs(), 'exec-out', 'screencap', '-p'], { timeout: 60_000 });
  // screencap writes its "Multiple displays" warning to stdout, which exec-out
  // merges into the stream; drop everything before the real PNG signature.
  const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47]);
  const start = png.indexOf(sig);
  if (start > 0) png = png.subarray(start);
  const path = join(tmpdir(), `brahma_phone_${Date.now()}.png`);
  await writeFile(path, png);
  let dims = 'unknown';
  if (png.length > 24 && png.subarray(0, 4).equals(sig)) {
    dims = `${png.readUInt32BE(16)}x${png.readUInt32BE(20)}`;
  }
  return textResult(`screenshot saved: ${path}\n${dims}, ${png.length} bytes\n(view it with an image-capable tool)`);
}

async function uiDump(maxNodes = 80) {
  await requireDevice();
  const remote = '/sdcard/brahma_ui.xml';
  await deviceAdb(['shell', 'rm', '-f', remote]).catch(() => {});
  const dumpOut = await deviceAdb(['shell', 'uiautomator', 'dump', remote]);
  if (!/UI hierchary dumped|dumped to/i.test(dumpOut) && !/dumped/i.test(dumpOut)) {
    // Fall through: some builds print nothing but still write the file.
  }
  const xml = await deviceAdb(['exec-out', 'cat', remote]).catch(() => '');
  if (!xml.includes('<hierarchy')) throw new Error(`uiautomator failed: ${dumpOut.trim()}`);

  const nodes = [...xml.matchAll(/<node\b([^>]*)\/?>/g)].map((m) => m[1]);
  const attr = (s, name) => {
    const re = new RegExp(`${name}="([^"]*)"`);
    return re.exec(s)?.[1] ?? '';
  };
  const rows = [];
  let clickIndex = 0;
  for (const raw of nodes) {
    const text = attr(raw, 'text');
    const desc = attr(raw, 'content-desc');
    const id = attr(raw, 'resource-id');
    const cls = attr(raw, 'class').replace('android.widget.', '').replace('android.view.', '');
    const bounds = attr(raw, 'bounds');
    const clickable = attr(raw, 'clickable') === 'true';
    const label = text || desc;
    if (!label && !clickable) continue;
    const m = /\[(\d+),(\d+)\]\[(\d+),(\d+)\]/.exec(bounds);
    let center = '';
    if (m) {
      const cx = Math.round((+m[1] + +m[3]) / 2);
      const cy = Math.round((+m[2] + +m[4]) / 2);
      center = clickable ? ` tap(${cx},${cy})` : ` @${cx},${cy}`;
    }
    const tag = clickable ? `[${clickIndex++}] ` : '     ';
    rows.push(`${tag}${label || cls}${id ? ` <${id}>` : ''}${center}`);
    if (rows.length >= maxNodes) break;
  }
  const header = `${nodes.length} nodes; ${clickIndex} clickable. Tap a [n] entry by its coordinates.\n`;
  return textResult(header + rows.join('\n'));
}

async function tap(x, y) {
  await requireDevice();
  await deviceAdb(['shell', 'input', 'tap', String(x), String(y)]);
  return textResult(`tapped ${x},${y}`);
}

async function swipe(x1, y1, x2, y2, durationMs) {
  await requireDevice();
  const dur = durationMs > 0 ? String(durationMs) : '300';
  await deviceAdb(['shell', 'input', 'swipe', String(x1), String(y1), String(x2), String(y2), dur]);
  return textResult(`swiped ${x1},${y1} -> ${x2},${y2} in ${dur}ms`);
}

async function typeText(text) {
  await requireDevice();
  // `input text` treats %s as space and needs quoting for shell metacharacters.
  const escaped = text.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/ /g, '%s');
  await deviceAdb(['shell', `input text "${escaped}"`]);
  return textResult(`typed: ${text}`);
}

const KEYS = {
  BACK: 'KEYCODE_BACK', HOME: 'KEYCODE_HOME', APP_SWITCH: 'KEYCODE_APP_SWITCH',
  RECENTS: 'KEYCODE_APP_SWITCH', POWER: 'KEYCODE_POWER', WAKEUP: 'KEYCODE_WAKEUP',
  SLEEP: 'KEYCODE_SLEEP', VOLUME_UP: 'KEYCODE_VOLUME_UP', VOLUME_DOWN: 'KEYCODE_VOLUME_DOWN',
  MUTE: 'KEYCODE_VOLUME_MUTE', MEDIA_PLAY_PAUSE: 'KEYCODE_MEDIA_PLAY_PAUSE',
  ENTER: 'KEYCODE_ENTER', DPAD_UP: 'KEYCODE_DPAD_UP', DPAD_DOWN: 'KEYCODE_DPAD_DOWN',
  DPAD_LEFT: 'KEYCODE_DPAD_LEFT', DPAD_RIGHT: 'KEYCODE_DPAD_RIGHT', DEL: 'KEYCODE_DEL',
  NOTIFICATION: 'KEYCODE_NOTIFICATION', CAMERA: 'KEYCODE_CAMERA',
};

async function key(keyName) {
  await requireDevice();
  const code = KEYS[keyName.toUpperCase()] ?? (/^\d+$/.test(keyName) ? keyName : keyName.toUpperCase());
  await deviceAdb(['shell', 'input', 'keyevent', code]);
  return textResult(`sent keyevent ${code}`);
}

async function launch(pkg) {
  await requireDevice();
  const out = await deviceAdb(['shell', 'monkey', '-p', pkg, '-c', 'android.intent.category.LAUNCHER', '1']);
  if (/Exception|Error|not exist/i.test(out)) throw new Error(out.trim());
  return textResult(`launched ${pkg}`);
}

async function listPackages(filter) {
  await requireDevice();
  const out = await deviceAdb(['shell', 'pm', 'list', 'packages']);
  const pkgs = out.split('\n').map((l) => l.replace('package:', '').trim()).filter(Boolean);
  const hits = filter ? pkgs.filter((p) => p.toLowerCase().includes(filter.toLowerCase())) : pkgs;
  return textResult(`${hits.length} packages${filter ? ` matching "${filter}"` : ''}:\n` + hits.slice(0, 200).join('\n'));
}

async function currentApp() {
  await requireDevice();
  const acts = await deviceAdb(['shell', 'dumpsys', 'activity', 'activities']).catch(() => '');
  const resumed = /topResumedActivity=ActivityRecord\{[^}]*\s(\S+?)\/(\S+?)\s/.exec(acts);
  if (resumed) return textResult(`foreground app: ${resumed[1]}\nactivity: ${resumed[2]}`);
  const focus = await deviceAdb(['shell', 'dumpsys', 'window', 'windows']).catch(() => '');
  const win = /mCurrentFocus=Window\{[^}]*\s(\S+?)\/(\S+?)\}/.exec(focus);
  if (win) return textResult(`focused window: ${win[1]}\nactivity: ${win[2]}`);
  return textResult('could not determine foreground app');
}

async function shell(command) {
  await requireDevice();
  const out = await deviceAdb(['shell', command], { timeout: 60_000 });
  return textResult(out || '(no output)');
}

/* ------------------------------------------------------------------ wire */

const server = new McpServer({ name: 'brahma-phone', version: '1.0.0' });

server.registerTool('phone_status', {
  description: 'Show connected device, model, Android version and battery.',
  inputSchema: {},
}, async () => {
  try { return await getStatus(); } catch (e) { return errorResult(e.message); }
});

server.registerTool('phone_connect', {
  description: 'Connect to the phone over TCP, e.g. host "127.0.0.1:5555". Run after adb pair.',
  inputSchema: { host: z.string().describe('host:port from Wireless debugging') },
}, async ({ host }) => {
  try { return textResult(await adb(['connect', host])); } catch (e) { return errorResult(e.message); }
});

server.registerTool('phone_capture', {
  description: 'Take a screenshot of the phone screen. Returns a file path you can open.',
  inputSchema: {},
}, async () => {
  try { return await capture(); } catch (e) { return errorResult(e.message); }
});

server.registerTool('phone_ui_dump', {
  description: 'Dump the current screen UI tree: visible texts and clickable elements with tap coordinates.',
  inputSchema: { max_nodes: z.number().int().min(10).max(300).optional() },
}, async ({ max_nodes } = {}) => {
  try { return await uiDump(max_nodes ?? 80); } catch (e) { return errorResult(e.message); }
});

server.registerTool('phone_tap', {
  description: 'Tap a pixel coordinate on the phone screen.',
  inputSchema: { x: z.number().int(), y: z.number().int() },
}, async ({ x, y }) => {
  try { return await tap(x, y); } catch (e) { return errorResult(e.message); }
});

server.registerTool('phone_swipe', {
  description: 'Swipe between two points (scroll, drag, unlock).',
  inputSchema: {
    x1: z.number().int(), y1: z.number().int(),
    x2: z.number().int(), y2: z.number().int(),
    duration_ms: z.number().int().min(0).max(5000).optional(),
  },
}, async (a) => {
  try { return await swipe(a.x1, a.y1, a.x2, a.y2, a.duration_ms); } catch (e) { return errorResult(e.message); }
});

server.registerTool('phone_type', {
  description: 'Type text into the focused field on the phone.',
  inputSchema: { text: z.string() },
}, async ({ text }) => {
  try { return await typeText(text); } catch (e) { return errorResult(e.message); }
});

server.registerTool('phone_key', {
  description: 'Press a key: BACK, HOME, APP_SWITCH, POWER, VOLUME_UP, ENTER, DPAD_*, or a numeric keycode.',
  inputSchema: { key: z.string() },
}, async ({ key: k }) => {
  try { return await key(k); } catch (e) { return errorResult(e.message); }
});

server.registerTool('phone_launch', {
  description: 'Launch an app by package name, e.g. com.whatsapp.',
  inputSchema: { package: z.string() },
}, async ({ package: pkg }) => {
  try { return await launch(pkg); } catch (e) { return errorResult(e.message); }
});

server.registerTool('phone_list_packages', {
  description: 'List installed packages, optionally filtered by substring.',
  inputSchema: { filter: z.string().optional() },
}, async ({ filter } = {}) => {
  try { return await listPackages(filter); } catch (e) { return errorResult(e.message); }
});

server.registerTool('phone_current_app', {
  description: 'Return the package/activity currently in the foreground.',
  inputSchema: {},
}, async () => {
  try { return await currentApp(); } catch (e) { return errorResult(e.message); }
});

server.registerTool('phone_shell', {
  description: 'Run an arbitrary adb shell command on the phone (advanced).',
  inputSchema: { command: z.string() },
}, async ({ command }) => {
  try { return await shell(command); } catch (e) { return errorResult(e.message); }
});

await server.connect(new StdioServerTransport());
