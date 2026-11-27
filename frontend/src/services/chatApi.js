import { api } from './axios';

export const chatApi = {
  send: (payload) => api.post('/chat', payload, { timeout: 35000 }),
};
