// RGDS diagnostics receiver. GPL-3.0-or-later. No email, KV, D1 or R2 required.
const json = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }
});
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === 'GET' && url.pathname === '/') {
      return json({ service: 'rgds-logger', protocol: 1, storage: 'observability', email: false });
    }
    if (request.method !== 'POST' || url.pathname !== '/log') return json({ error: 'not_found' }, 404);
    if (!env.RGDS_API_KEY || env.RGDS_API_KEY.length < 32) return json({ error: 'secret_not_configured' }, 503);
    if (request.headers.get('Authorization') !== `Bearer ${env.RGDS_API_KEY}`) return json({ error: 'unauthorized' }, 401);
    if (!(request.headers.get('Content-Type') || '').toLowerCase().startsWith('application/json')) return json({ error: 'json_required' }, 415);
    const reader = request.body?.getReader();
    if (!reader) return json({ error: 'body_required' }, 400);
    const chunks = []; let size = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > 128 * 1024) { await reader.cancel(); return json({ error: 'size_limit' }, 413); }
      chunks.push(value);
    }
    const bytes = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    let body;
    try { body = JSON.parse(new TextDecoder().decode(bytes)); } catch { return json({ error: 'invalid_json' }, 400); }
    if (!body || body.schema !== 1 || typeof body.reportId !== 'string'
        || !/^[a-f0-9-]{36}$/.test(body.reportId) || typeof body.log !== 'string' || body.log.length > 16000
        || typeof body.version !== 'string' || body.version.length > 100
        || typeof body.session !== 'string' || body.session.length > 200) return json({ error: 'invalid_report' }, 400);
    const receivedAt = new Date().toISOString();
    // Explicit allowlist: never record Authorization, the secret, or arbitrary request headers.
    console.log(JSON.stringify({ event: 'RGDS_DIAGNOSTIC', reportId: body.reportId, version: body.version,
      session: body.session, log: body.log, logTruncated: body.logTruncated === true, receivedAt }));
    return json({ ok: true, reportId: body.reportId, status: 'worker_received', authenticated: true,
      receivedAt, storage: 'observability', emailSent: false });
  }
};
