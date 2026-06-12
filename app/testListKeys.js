const http = require('https');

function requestGet(url) {
    return new Promise((resolve, reject) => {
        const parsedUrl = new URL(url);
        const options = {
            hostname: parsedUrl.hostname,
            port: 443,
            path: parsedUrl.pathname + parsedUrl.search,
            method: 'GET',
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            }
        };

        const req = http.request(options, (res) => {
            let body = '';
            res.on('data', (chunk) => body += chunk);
            res.on('end', () => {
                resolve({
                    statusCode: res.statusCode,
                    headers: res.headers,
                    body: body
                });
            });
        });

        req.on('error', reject);
        req.end();
    });
}

async function run() {
    const bucketId = '6h9NTtLDbTocxLkyC7Jpv6';
    try {
        console.log(`1. Fetching keys from https://kvdb.io/${bucketId}/ ...`);
        const res1 = await requestGet(`https://kvdb.io/${bucketId}/`);
        console.log('Status Base URL:', res1.statusCode);
        console.log('Body Base URL:', res1.body);

        console.log(`\n2. Fetching keys from https://kvdb.io/${bucketId}/?prefix=user_ ...`);
        const res2 = await requestGet(`https://kvdb.io/${bucketId}/?prefix=user_`);
        console.log('Status with prefix:', res2.statusCode);
        console.log('Body with prefix:', res2.body);

        console.log(`\n3. Fetching keys from https://kvdb.io/${bucketId}/values ...`);
        const res3 = await requestGet(`https://kvdb.io/${bucketId}/values`);
        console.log('Status with values:', res3.statusCode);
        console.log('Body with values:', res3.body);

    } catch (e) {
        console.error('Error:', e);
    }
}

run();
