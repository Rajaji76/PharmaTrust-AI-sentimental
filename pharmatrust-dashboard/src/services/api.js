import axios from 'axios'

const API_BASE_URL = 'http://localhost:8080/api/v1'

// Create axios instance with default config
const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Manufacturer APIs
export const manufacturerAPI = {
  createBatch: async (batchData) => {
    const response = await api.post('/batches/create', batchData)
    return response.data
  },
  
  getAllBatches: async () => {
    const response = await api.get('/batches')
    return response.data
  },
}

// Patient/Verification APIs
export const verificationAPI = {
  checkMedicine: async (serialNumber) => {
    const response = await api.get(`/batches/check`, {
      params: { sn: serialNumber }
    })
    return response.data
  },
  
  verifyBySerial: async (serialNumber) => {
    const response = await api.get(`/verify/${serialNumber}`)
    return response.data
  },
}

// Regulator APIs
export const regulatorAPI = {
  getAllUnits: async () => {
    const response = await api.get('/units/all')
    return response.data
  },
  
  getAllBatches: async () => {
    const response = await api.get('/batches/all')
    return response.data
  },
  
  getAlerts: async () => {
    const response = await api.get('/alerts')
    return response.data
  },
}

// Distributor APIs
export const distributorAPI = {
  getShipments: async () => {
    const response = await api.get('/shipments')
    return response.data
  },
}

// Retailer APIs
export const retailerAPI = {
  getInventory: async () => {
    const response = await api.get('/inventory')
    return response.data
  },
}

export default api
