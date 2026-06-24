const http = require('http');
const https = require('https');
const fs = require('fs');

const id = process.argv[2] || '1';
const url = `http://localhost:3000/api/reports/vehiculo/${id}/estado_actual.pdf`;

function fetch(url, outPath) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https') ? https : http;
    const req = lib.get(url, (res) => {
      const { statusCode, headers } = res;
      const file = fs.createWriteStream(outPath);
      const chunks = [];
      res.on('data', (chunk) => {
        chunks.push(chunk);
      });
      res.pipe(file);
      file.on('finish', () => {
        file.close(() => {
          const body = Buffer.concat(chunks);
          resolve({ statusCode, headers, body });
        });
      });
      res.on('error', (err) => reject(err));
    });
    req.on('error', (err) => reject(err));
  });
}

(async () => {
  try {
    const outPath = `vehiculo_${id}_estado_actual.pdf`;
    console.log(`Requesting ${url}`);
    const { statusCode, headers, body } = await fetch(url, outPath);
    console.log('Status:', statusCode);
    console.log('Content-Type:', headers['content-type']);
    console.log('Content-Length:', headers['content-length']);
    console.log('Saved to:', outPath);

    const head = body.slice(0, 256);
    console.log('First bytes (hex):', head.toString('hex').match(/.{1,2}/g).join(' '));
    console.log('Snippet (utf8):', head.toString('utf8').slice(0, 512));
  } catch (err) {
    console.error('Error fetching report:', err);
    process.exit(1);
  }
})();
