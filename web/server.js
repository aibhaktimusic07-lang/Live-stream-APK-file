const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const WEB_DIR = __dirname;
const ROOT_DIR = path.resolve(__dirname, '..');

const MIME_TYPES = {
  '.html': 'text/html; charset=UTF-8',
  '.js': 'application/javascript; charset=UTF-8',
  '.css': 'text/css; charset=UTF-8',
  '.json': 'application/manifest+json; charset=UTF-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.apk': 'application/vnd.android.package-archive'
};

const server = http.createServer((req, res) => {
  let reqPath = decodeURI(req.url.split('?')[0]);
  if (reqPath === '/') reqPath = '/index.html';

  // Handle direct APK download
  if (reqPath === '/app-debug.apk') {
    const apkCandidates = [
      path.join(ROOT_DIR, '.build-outputs', 'app-debug.apk'),
      path.join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')
    ];
    for (const apkPath of apkCandidates) {
      if (fs.existsSync(apkPath)) {
        res.writeHead(200, {
          'Content-Type': 'application/vnd.android.package-archive',
          'Content-Disposition': 'attachment; filename="247-Live-Studio.apk"',
          'Content-Length': fs.statSync(apkPath).size
        });
        return fs.createReadStream(apkPath).pipe(res);
      }
    }
  }

  const filePath = path.join(WEB_DIR, reqPath);

  // Security check: ensure inside WEB_DIR
  if (!filePath.startsWith(WEB_DIR)) {
    res.writeHead(403);
    return res.end('Forbidden');
  }

  fs.stat(filePath, (err, stats) => {
    if (err || !stats.isFile()) {
      // Fallback for PWA navigation
      if (req.headers.accept && req.headers.accept.includes('text/html')) {
        const indexPath = path.join(WEB_DIR, 'index.html');
        res.writeHead(200, { 'Content-Type': 'text/html; charset=UTF-8' });
        return fs.createReadStream(indexPath).pipe(res);
      }
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      return res.end('404 Not Found');
    }

    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    const headers = {
      'Content-Type': contentType,
      'Content-Length': stats.size,
      'Access-Control-Allow-Origin': '*'
    };

    // Service worker header
    if (ext === '.js' && filePath.endsWith('sw.js')) {
      headers['Service-Worker-Allowed'] = '/';
      headers['Cache-Control'] = 'no-cache';
    }

    res.writeHead(200, headers);
    fs.createReadStream(filePath).pipe(res);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[PWA Server] 24/7 Live Studio PWA server running at http://0.0.0.0:${PORT}`);
});
