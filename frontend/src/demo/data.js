const KEY = 'foodrescue_demo_v2';
const initial = {
  stores: [
    { id: 1, ownerId: 2, name: 'Bánh mì An Phát', owner: 'owner@gmail.com', address: '105 An Dương Vương, Quận 5', status: 'APPROVED', commissionRate: 15, rating: 4.8 },
    { id: 2, ownerId: 3, name: 'Sweet Paris Bakery', owner: 'sweetparis@gmail.com', address: '45 Lê Duẩn, Quận 1', status: 'APPROVED', commissionRate: 15, rating: 4.7 },
    { id: 3, ownerId: 4, name: 'Bếp Nhà Xanh', owner: 'bepxanh@gmail.com', address: '12 Nguyễn Thị Minh Khai, Quận 1', status: 'PENDING', commissionRate: 15, rating: 0 },
  ],
  categories: [
    { id: 1, storeId: 1, name: 'Bánh mì', description: 'Bánh mì và các món ăn nhanh làm trong ngày.', imageUrl: '/category-bread.svg' },
    { id: 2, storeId: 2, name: 'Bánh ngọt', description: 'Bánh ngọt và món tráng miệng cuối ngày.', imageUrl: '/category-cake.svg' },
    { id: 3, storeId: 1, name: 'Cơm và món chính', description: 'Bữa ăn chính còn ngon, sẵn sàng mang về.', imageUrl: '/category-meal.svg' },
    { id: 2004, storeId: 2, name: 'Bánh mì', description: 'Bánh mì mới làm tại cửa hàng.', imageUrl: '/category-bread.svg' },
    { id: 2005, storeId: 2, name: 'Bữa ăn nhẹ', description: 'Món ăn nhẹ phù hợp để mang về.', imageUrl: '/category-meal.svg' },
  ],
  batches: [
    { id: 1, storeId: 1, categoryId: 1, title: 'Túi bánh mì cuối ngày', originalPrice: 80000, rescuePrice: 30000, quantity: 9, remaining: 8, status: 'AVAILABLE', pickup: '20:00–21:30' },
    { id: 2, storeId: 2, categoryId: 2, title: 'Hộp bánh ngọt bất ngờ', originalPrice: 120000, rescuePrice: 45000, quantity: 7, remaining: 6, status: 'AVAILABLE', pickup: '20:30–22:00' },
    { id: 3, storeId: 1, categoryId: 3, title: 'Bữa tối tiết kiệm', originalPrice: 90000, rescuePrice: 39000, quantity: 4, remaining: 4, status: 'AVAILABLE', pickup: '18:30–20:00' },
    { id: 2004, storeId: 2, categoryId: 2, title: 'Túi bánh ngọt cuối ngày', originalPrice: 100000, rescuePrice: 38000, quantity: 8, remaining: 5, status: 'AVAILABLE', pickup: '19:30–21:00' },
    { id: 2005, storeId: 2, categoryId: 2004, title: 'Túi bánh mì mới nướng', originalPrice: 75000, rescuePrice: 28000, quantity: 10, remaining: 7, status: 'AVAILABLE', pickup: '18:00–20:00' },
    { id: 2006, storeId: 2, categoryId: 2005, title: 'Hộp ăn nhẹ bất ngờ', originalPrice: 85000, rescuePrice: 32000, quantity: 6, remaining: 4, status: 'AVAILABLE', pickup: '20:00–21:30' },
  ],
  cart: [],
  orders: [
    { id: 101, customer: 'Khách mẫu', storeId: 1, items: [{ batchId: 1, quantity: 1, price: 30000 }], total: 30000, status: 'CONFIRMED', paymentStatus: 'PAID', pickupCode: 'FR101', createdAt: '22/11/2026 10:15' },
    { id: 102, customer: 'Khách mẫu', storeId: 2, items: [{ batchId: 2, quantity: 1, price: 45000 }], total: 45000, status: 'COMPLETED', paymentStatus: 'PAID', pickupCode: 'FR102', createdAt: '21/11/2026 17:30' },
    { id: 103, customer: 'Khách mẫu', storeId: 1, items: [{ batchId: 3, quantity: 1, price: 39000 }], total: 39000, status: 'CANCELLED', paymentStatus: 'REFUND_PENDING', pickupCode: 'FR103', createdAt: '20/11/2026 18:00' },
  ],
  refunds: [{ id: 1, orderId: 103, amount: 39000, status: 'PENDING', reason: 'STORE_CANCELLED' }],
  payouts: [{ id: 1, storeId: 1, amount: 25500, status: 'PENDING', period: '01–15/11/2026' }],
  reviews: [{ id: 1, storeId: 2, orderId: 102, rating: 5, comment: 'Bánh ngon, nhận hàng nhanh' }],
  audit: [{ id: 1, action: 'DEMO_INITIALIZED', actor: 'Hệ thống', time: '22/11/2026' }],
  storeCatalogSeedVersion: 1,
};

export function readDemo() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY));
    if (!saved) return structuredClone(initial);
    let migrated = false;
    if (saved.categories.some(category => category.storeId == null)) {
      migrated = true;
      const categories = [];
      const unassigned = [];
      let nextId = Math.max(0, ...saved.categories.map(category => category.id));
      for (const category of saved.categories) {
        const stores = [...new Set(saved.batches.filter(batch => batch.categoryId === category.id).map(batch => batch.storeId))];
        if (!stores.length) unassigned.push(category);
        for (const storeId of stores) {
          const id = categories.some(row => row.id === category.id) ? ++nextId : category.id;
          categories.push({ ...category, id, storeId });
          saved.batches.filter(batch => batch.categoryId === category.id && batch.storeId === storeId)
            .forEach(batch => { batch.categoryId = id; });
        }
      }
      saved.categories = categories;
      saved.legacyUnassignedCategories = unassigned;
    }
    if (!saved.storeCatalogSeedVersion) {
      for (const category of initial.categories.filter(row => row.id >= 2004)) {
        if (!saved.categories.some(row => row.id === category.id)) saved.categories.push(structuredClone(category));
      }
      for (const batch of initial.batches.filter(row => row.id >= 2004)) {
        if (!saved.batches.some(row => row.id === batch.id)) saved.batches.push(structuredClone(batch));
      }
      saved.storeCatalogSeedVersion = 1;
      migrated = true;
    }
    let enriched = false;
    saved.categories.forEach(category => {
      if (!category.description) { category.description = ''; enriched = true; }
      if (!category.imageUrl) {
        category.imageUrl = category.name.toLowerCase().includes('bánh mì') ? '/category-bread.svg'
          : category.name.toLowerCase().includes('bánh') ? '/category-cake.svg' : '/category-meal.svg';
        enriched = true;
      }
    });
    if (migrated || enriched) localStorage.setItem(KEY, JSON.stringify(saved));
    return saved;
  }
  catch { return structuredClone(initial); }
}
export function writeDemo(next) {
  localStorage.setItem(KEY, JSON.stringify(next));
  window.dispatchEvent(new Event('foodrescue:demo-change'));
}
export function updateDemo(change) {
  const next = structuredClone(readDemo());
  change(next);
  writeDemo(next);
  return next;
}
export function resetDemo() { writeDemo(structuredClone(initial)); }
export const money = value => `${Number(value || 0).toLocaleString('vi-VN')} đ`;
