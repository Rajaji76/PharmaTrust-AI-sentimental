import axios from 'axios'

// Use relative URL so all requests go through Vite's proxy (/api → backend)
// This avoids CSP connect-src issues and works on any network (mobile, desktop, etc.)
const API_BASE_URL = '/api/v1'

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('authToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Auth APIs
export const authAPI = {
  register: (data) => api.post('/auth/register', data).then(r => r.data),
  login: (data) => api.post('/auth/login', data).then(r => r.data),
  logout: () => api.post('/auth/logout').then(r => r.data),
  getCurrentUser: () => api.get('/auth/me').then(r => r.data),
  getMyVerificationStatus: () => api.get('/auth/my-verification-status').then(r => r.data).catch(() => ({ isVerified: false })),
}

// Batch APIs
export const batchAPI = {
  createBatch: (data) => api.post('/batches', data).then(r => r.data),
  createBatchComplete: (formData) =>
    api.post('/batches/create-complete', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then(r => r.data),
  getMyBatches: () => api.get('/batches/my-batches').then(r => r.data),
  getBatchDetails: (batchId) => api.get(`/batches/${batchId}`).then(r => r.data),
  getLabReport: (batchNumber) => api.get(`/batches/lab-report/${batchNumber}`).then(r => r.data),
  approveBatch: (batchId, digitalSignature, approvalType = 'PRODUCTION_HEAD') =>
    api.post(`/batches/${batchId}/approve`, { approvalType, digitalSignature }).then(r => r.data),
  recallBatch: (batchId, reason) =>
    api.post(`/batches/${batchId}/recall`, { reason }).then(r => r.data),
  quarantineBatch: (batchId, reason) =>
    api.post(`/batches/${batchId}/quarantine`, { reason }).then(r => r.data),
  getPendingApprovals: () => api.get('/batches/pending-approvals').then(r => r.data),
}

// Scan APIs
export const scanAPI = {
  scanUnit: (serialNumber) =>
    api.post('/scan', { serialNumber }).then(r => r.data),
  verifyOffline: (serialNumber, totpCode) =>
    api.post('/verify/offline', { serialNumber, totpCode }).then(r => r.data),
  getScanHistory: () => api.get('/scan/history').then(r => r.data).catch(() => []),
}

// Verify APIs
export const verifyAPI = {
  verifyUnit: (serialNumber) =>
    api.get(`/verify/${serialNumber}`).then(r => r.data),
  verifyOffline: (serialNumber, totpCode) =>
    api.post('/verify/offline', { serialNumber, totpCode }).then(r => r.data),
  getUnitHistory: (serialNumber) =>
    api.get(`/verify/${serialNumber}/history`).then(r => r.data),
  getBoxDetails: (serialNumber) =>
    api.get(`/verify/box/${serialNumber}`).then(r => r.data),
}

// Transfer APIs
export const transferAPI = {
  initiateTransfer: (data) =>
    api.post('/transfer/initiate', data).then(r => r.data),
  acceptTransfer: (transferId) =>
    api.post(`/transfer/${transferId}/accept`).then(r => r.data),
  rejectTransfer: (transferId, reason) =>
    api.post(`/transfer/${transferId}/reject`, { reason }).then(r => r.data),
  getPendingTransfers: () =>
    api.get('/transfer/pending').then(r => r.data),
  getMyTransfers: () =>
    api.get('/transfer/my-history').then(r => r.data).catch(() => []),
  getTransferHistory: (serialNumber) =>
    api.get(`/transfer/history/${serialNumber}`).then(r => r.data),
  receiveStock: (serialNumber, notes) =>
    api.post('/transfer/receive', { serialNumber, notes }).then(r => r.data),
  getMyStock: () =>
    api.get('/transfer/my-stock').then(r => r.data),
}

// Job APIs
export const jobAPI = {
  getJobStatus: (jobId) => api.get(`/jobs/${jobId}`).then(r => r.data),
  getMyJobs: () => api.get('/jobs/my-jobs').then(r => r.data),
  retryJob: (jobId) => api.post(`/jobs/${jobId}/retry`).then(r => r.data),
  cancelJob: (jobId) => api.post(`/jobs/${jobId}/cancel`).then(r => r.data),
}

// Regulator APIs
export const regulatorAPI = {
  getStats: () => api.get('/regulator/stats').then(r => r.data),
  getAlerts: () => api.get('/regulator/alerts').then(r => r.data),
  getRecallEvents: () => api.get('/regulator/recalls').then(r => r.data),
  getAuditLogs: () => api.get('/regulator/audit-logs').then(r => r.data),
  getAllBatches: () => api.get('/regulator/batches').then(r => r.data).catch(() => []),
  getBatchLocation: (batchNumber) => api.get(`/regulator/batch-location/${batchNumber}`).then(r => r.data),
  getRegisteredPartners: () => api.get('/regulator/registered-partners').then(r => r.data).catch(() => ({ distributors: [], retailers: [], totalDistributors: 0, totalRetailers: 0 })),
  verifyPartner: (userId) => api.post(`/regulator/verify-partner/${userId}`).then(r => r.data),
  unverifyPartner: (userId) => api.post(`/regulator/unverify-partner/${userId}`).then(r => r.data),
}

// Supply Chain APIs
export const supplyChainAPI = {
  getStats: () => api.get('/supply-chain/stats').then(r => r.data),
}

// Security APIs
export const securityAPI = {
  killHierarchy: (parentSerialNumber, reason) =>
    api.post('/security/kill-hierarchy', { parentSerialNumber, reason }).then(r => r.data),
  getHierarchyStats: (serialNumber) =>
    api.get(`/security/hierarchy-stats/${serialNumber}`).then(r => r.data),
  checkIfBlocked: (serialNumber) =>
    api.get(`/security/check-blocked/${serialNumber}`).then(r => r.data),
  generateTOTP: (serialNumber, secret) =>
    api.post('/security/generate-totp', { serialNumber, secret }).then(r => r.data),
  verifyTOTP: (serialNumber, totp, secret) =>
    api.post('/security/verify-totp', { serialNumber, totp, secret }).then(r => r.data),
}

// Complaint APIs (Distributor/Retailer inspection reports)
export const complaintAPI = {
  raiseComplaint: (data) => api.post('/complaints/raise', data).then(r => r.data),
  getMyComplaints: () => api.get('/complaints/my').then(r => r.data).catch(() => []),
  getAllComplaints: () => api.get('/complaints/all').then(r => r.data).catch(() => []),
  getComplaintById: (id) => api.get(`/complaints/${id}`).then(r => r.data).catch(() => null),
}

// Government Scheme Dispense APIs
export const govtSchemeAPI = {
  lookupPatient: (data) => api.post('/govt-scheme/lookup-patient', data).then(r => r.data),
  checkSafety: (abhaId, medicineName) =>
    api.post('/govt-scheme/check-safety', { abhaId, medicineName }).then(r => r.data),
  recordDispense: (data) => api.post('/govt-scheme/dispense', data).then(r => r.data),
  getMyDispenseHistory: () => api.get('/govt-scheme/my-dispense-history').then(r => r.data).catch(() => []),
}

export default api
