// Test script for llantas-inspeccion endpoints
const http = require('http');

// Configuration
const BASE_URL = 'http://localhost:3000';
const API_ENDPOINT = '/api/llantas-inspeccion';

// Helper function to make HTTP requests
function makeRequest(method, path, data = null) {
    return new Promise((resolve, reject) => {
        const url = new URL(BASE_URL + path);
        const options = {
            hostname: url.hostname,
            port: url.port || 3000,
            path: url.pathname + url.search,
            method: method,
            headers: {
                'Content-Type': 'application/json'
            }
        };

        const req = http.request(options, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                try {
                    const parsed = body ? JSON.parse(body) : null;
                    resolve({ status: res.statusCode, data: parsed, raw: body });
                } catch (e) {
                    resolve({ status: res.statusCode, data: null, raw: body });
                }
            });
        });

        req.on('error', reject);

        if (data) {
            req.write(JSON.stringify(data));
        }
        req.end();
    });
}

// Test functions
async function testCreateSingleLlanta() {
    console.log('\n=== Test 1: Create single llanta-inspeccion ===');
    const payload = {
        vehiculosinspeccion_idVehiculoInspeccion: 1,
        Llantas_idLlantas: 1,
        LlantasInspeccionMm1: 5.5,
        LlantasInspeccionMm2: 6.0,
        LlantasInspeccionMm3: 5.8,
        LlantasInspeccionMm4: 5.9,
        LlantasInspeccionPresion: 28.5,
        LlantasInspeccionCondPel: 0,
        LlantasInspeccionObservacion: "Test observation",
        LlantasInspeccionComentario: "Test comment",
        LlantasInspeccionDOT: "2023",
        LlantasInspeccionPiso: "buen estado",
        LlantasInspeccionDesgaste: "normal"
    };

    try {
        const response = await makeRequest('POST', API_ENDPOINT, payload);
        console.log('Status:', response.status);
        console.log('Response:', JSON.stringify(response.data, null, 2));
        return response.status === 201;
    } catch (error) {
        console.error('Error:', error.message);
        return false;
    }
}

async function testCreateBatchLlantas() {
    console.log('\n=== Test 2: Create batch llantas-inspeccion ===');
    const payload = [
        {
            vehiculosinspeccion_idVehiculoInspeccion: 1,
            Llantas_idLlantas: 1,
            LlantasInspeccionMm1: 5.5,
            LlantasInspeccionMm2: 6.0,
            LlantasInspeccionMm3: 5.8,
            LlantasInspeccionMm4: 5.9,
            LlantasInspeccionPresion: 28.5,
            LlantasInspeccionCondPel: 0,
            LlantasInspeccionObservacion: "Test 1",
            LlantasInspeccionComentario: "Comment 1",
            LlantasInspeccionDOT: "2023",
            LlantasInspeccionPiso: "buen estado",
            LlantasInspeccionDesgaste: "normal"
        },
        {
            vehiculosinspeccion_idVehiculoInspeccion: 1,
            Llantas_idLlantas: 2,
            LlantasInspeccionMm1: 6.0,
            LlantasInspeccionMm2: 6.1,
            LlantasInspeccionMm3: 5.9,
            LlantasInspeccionMm4: 6.0,
            LlantasInspeccionPresion: 29.0,
            LlantasInspeccionCondPel: 0,
            LlantasInspeccionObservacion: "Test 2",
            LlantasInspeccionComentario: "Comment 2",
            LlantasInspeccionDOT: "2023",
            LlantasInspeccionPiso: "buen estado",
            LlantasInspeccionDesgaste: "normal"
        }
    ];

    try {
        const response = await makeRequest('POST', API_ENDPOINT + '/batch', payload);
        console.log('Status:', response.status);
        console.log('Response:', JSON.stringify(response.data, null, 2));
        return response.status === 201;
    } catch (error) {
        console.error('Error:', error.message);
        return false;
    }
}

async function testGetAll() {
    console.log('\n=== Test 3: Get all llantas-inspeccion ===');
    try {
        const response = await makeRequest('GET', API_ENDPOINT);
        console.log('Status:', response.status);
        console.log('Count:', response.data ? response.data.length : 0);
        if (response.data && response.data.length > 0) {
            console.log('First record:', JSON.stringify(response.data[0], null, 2));
        }
        return response.status === 200;
    } catch (error) {
        console.error('Error:', error.message);
        return false;
    }
}

// Run all tests
async function runAllTests() {
    console.log('Starting llantas-inspeccion endpoint tests...');
    console.log('Base URL:', BASE_URL);
    
    const results = {
        testCreateSingleLlanta: await testCreateSingleLlanta(),
        testCreateBatchLlantas: await testCreateBatchLlantas(),
        testGetAll: await testGetAll()
    };

    console.log('\n=== Test Results ===');
    Object.entries(results).forEach(([name, passed]) => {
        console.log(`${name}: ${passed ? '✓ PASSED' : '✗ FAILED'}`);
    });

    const allPassed = Object.values(results).every(r => r);
    console.log(`\nOverall: ${allPassed ? '✓ ALL TESTS PASSED' : '✗ SOME TESTS FAILED'}`);
    process.exit(allPassed ? 0 : 1);
}

// Run tests
runAllTests().catch(error => {
    console.error('Fatal error:', error);
    process.exit(1);
});
