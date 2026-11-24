export function distanceKm(fromLat, fromLon, toLat, toLon) {
  const values = [fromLat, fromLon, toLat, toLon].map(Number);
  if (values.some(value => !Number.isFinite(value))) return null;
  if ([fromLat, fromLon, toLat, toLon].some(value => value == null || value === '')) return null;
  const [lat1, lon1, lat2, lon2] = values.map(value => value * Math.PI / 180);
  const deltaLat = lat2 - lat1;
  const deltaLon = lon2 - lon1;
  const a = Math.sin(deltaLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function storeDistance(user, store) {
  return distanceKm(user?.latitude, user?.longitude, store?.latitude, store?.longitude);
}

export function formatDistance(value) {
  return value == null ? null : `${value < 1 ? Math.round(value * 1000) + ' m' : value.toFixed(1) + ' km'} đường thẳng`;
}
