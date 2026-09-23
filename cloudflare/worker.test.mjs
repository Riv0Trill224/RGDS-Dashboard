import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.mjs';
const key = 'synthetic-test-key-not-a-secret-123456';
const payload = {schema:1,reportId:'12345678-1234-1234-1234-123456789abc',version:'test',session:'test.log',log:'fixture'};
function request(body, token=key) {
  return new Request('https://example.test/log',{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`},body:JSON.stringify(body)});
}
test('requires configured secret and authentication', async()=>{
  assert.equal((await worker.fetch(request(payload),{})).status,503);
  assert.equal((await worker.fetch(request(payload,'wrong'),{RGDS_API_KEY:key})).status,401);
});
test('acknowledges matching report without claiming email delivery',async()=>{
  const response=await worker.fetch(request(payload),{RGDS_API_KEY:key});
  assert.equal(response.status,200); const result=await response.json();
  assert.equal(result.reportId,payload.reportId); assert.equal(result.status,'worker_received');
  assert.equal(result.authenticated,true); assert.equal(result.emailSent,false);
});
test('rejects malformed and oversized reports',async()=>{
  assert.equal((await worker.fetch(request({...payload,reportId:'wrong'}),{RGDS_API_KEY:key})).status,400);
  assert.equal((await worker.fetch(request({...payload,log:'x'.repeat(16001)}),{RGDS_API_KEY:key})).status,400);
  assert.equal((await worker.fetch(request({...payload,log:'x'.repeat(140000)}),{RGDS_API_KEY:key})).status,413);
});
