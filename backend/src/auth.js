import crypto from 'node:crypto';
import { db } from './db.js';

/** scrypt 密码哈希（Node 内置，无依赖）。 */
export function hashPassword(password, salt) {
  return crypto.scryptSync(password, salt, 32).toString('hex');
}

function newSalt() {
  return crypto.randomBytes(16).toString('hex');
}

/** 对外可见的用户信息（不含密码）。 */
export function publicUser(u) {
  return {
    id: u.id,
    username: u.username,
    appUserId: u.appUserId,
    linkedPlayerUuid: u.linkedPlayerUuid,
    linkedPlayerName: u.linkedPlayerName,
    linkedAt: u.linkedAt,
    createdAt: u.createdAt,
  };
}

export function register(username, password) {
  if (!username || username.length < 3 || !password || password.length < 6) {
    throw err(400, 'bad_request', '用户名至少 3 位，密码至少 6 位');
  }
  if (db.findByUsername(username)) {
    throw err(409, 'username_taken', '用户名已存在');
  }
  const salt = newSalt();
  const user = db.createUser(username, hashPassword(password, salt), salt);
  return { token: db.createSession(user.id), user: publicUser(user) };
}

export function login(username, password) {
  const u = db.findByUsername(username || '');
  if (!u || u.passwordHash !== hashPassword(password || '', u.salt)) {
    throw err(401, 'bad_credentials', '用户名或密码错误');
  }
  return { token: db.createSession(u.id), user: publicUser(u) };
}

/** Express 中间件：Bearer token → req.user。 */
export function requireAuth(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  const session = token && db.getSession(token);
  if (!session) {
    return res.status(401).json({ ok: false, error: 'unauthorized' });
  }
  const user = db.findById(session.userId);
  if (!user) {
    return res.status(401).json({ ok: false, error: 'unauthorized' });
  }
  req.user = user;
  req.token = token;
  next();
}

export function err(status, code, message) {
  const e = new Error(message || code);
  e.status = status;
  e.code = code;
  return e;
}
