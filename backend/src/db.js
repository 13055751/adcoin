import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { config } from './config.js';

/**
 * 极简 JSON 存储：users + sessions（token → userId）。
 * 用户量小（几百~几千），JSON 文件足够；数据丢失容忍度高可换 SQLite。
 */
const DB_FILE = path.resolve(config.dbFile);

let state = { users: [], sessions: {} };

function load() {
  try {
    state = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
  } catch {
    state = { users: [], sessions: {} };
  }
}

function save() {
  fs.mkdirSync(path.dirname(DB_FILE), { recursive: true });
  fs.writeFileSync(DB_FILE, JSON.stringify(state, null, 2));
}

load();

export const db = {
  findByUsername(username) {
    const q = username.toLowerCase();
    return state.users.find((u) => u.username.toLowerCase() === q);
  },
  findById(id) {
    return state.users.find((u) => u.id === id);
  },
  findByAppUserId(appUserId) {
    return state.users.find((u) => u.appUserId === appUserId);
  },
  searchByUsername(q) {
    const query = q.toLowerCase();
    return state.users.filter((u) => u.username.toLowerCase().includes(query)).slice(0, 20);
  },
  createUser(username, passwordHash, salt) {
    const user = {
      id: 'u_' + crypto.randomBytes(8).toString('hex'),
      username,
      passwordHash,
      salt,
      appUserId: 'app_' + crypto.randomBytes(8).toString('hex'),
      linkedPlayerUuid: null,
      linkedPlayerName: null,
      linkedAt: null,
      createdAt: Date.now(),
    };
    state.users.push(user);
    save();
    return user;
  },
  updateUser(id, patch) {
    const u = state.users.find((x) => x.id === id);
    if (u) {
      Object.assign(u, patch);
      save();
    }
    return u;
  },
  createSession(userId) {
    const token = crypto.randomBytes(24).toString('hex');
    state.sessions[token] = { userId, createdAt: Date.now() };
    save();
    return token;
  },
  getSession(token) {
    return state.sessions[token] || null;
  },
  deleteSession(token) {
    delete state.sessions[token];
    save();
  },
};
