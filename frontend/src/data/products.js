// Dữ liệu mẫu để dựng giao diện. Khi có module Sản phẩm ở backend, thay bằng get('/api/products').
export const INITIAL_CATEGORY_TREE = [
  {
    id: 'root-pc',
    name: 'PC Build Sẵn (Nguyên bộ)',
    icon: '🖥️',
    children: [
      {
        id: 'prebuildpc',
        name: 'PCP Built',
        icon: '🖥️',
        subCategories: ['PCP Built', 'Gaming', 'Đồ họa', 'Văn phòng'],
      },
    ],
  },
  {
    id: 'root-parts',
    name: 'Linh kiện máy tính',
    icon: '⚙️',
    children: [
      { id: 'cpu', name: 'CPU – Bộ vi xử lý', icon: '🧠', subCategories: ['Intel', 'AMD'] },
      { id: 'mainboard', name: 'Mainboard – Bo mạch chủ', icon: '🔌', subCategories: ['MSI', 'Gigabyte', 'ASUS'] },
      { id: 'ram', name: 'RAM – Bộ nhớ trong', icon: '💾', subCategories: ['Kingston', 'Corsair', 'G.Skill'] },
      { id: 'vga', name: 'VGA – Card đồ họa', icon: '🎮', subCategories: ['MSI', 'Gigabyte', 'Sapphire', 'ASUS'] },
      { id: 'ssd', name: 'SSD – Ổ cứng', icon: '💽', subCategories: ['Samsung', 'WD', 'Kingston'] },
      { id: 'psu', name: 'PSU – Nguồn máy tính', icon: '⚡', subCategories: ['Cooler Master', 'Corsair', 'MSI'] },
      { id: 'case', name: 'Case – Vỏ máy tính', icon: '🖼️', subCategories: ['Xigmatek', 'NZXT', 'Lian Li'] },
      { id: 'cooler', name: 'Tản nhiệt CPU', icon: '❄️', subCategories: ['Deepcool', 'Thermalright', 'Noctua'] },
    ],
  },
  {
    id: 'root-gear',
    name: 'Màn hình & Ngoại vi',
    icon: '🖵',
    children: [
      { id: 'monitor', name: 'Màn hình máy tính', icon: '🖵', subCategories: ['LG', 'Dell', 'ASUS', 'Samsung'] },
    ],
  },
];
export const CATEGORY_ATTR_SCHEMA = {
  prebuildpc: [
    { key: 'cpuModel', label: 'Vi xử lý (CPU)', type: 'text', placeholder: 'VD: Core i5-14400F' },
    { key: 'vgaModel', label: 'Card đồ họa (VGA)', type: 'text', placeholder: 'VD: RTX 4060 8GB' },
    { key: 'ramInfo', label: 'Dung lượng RAM', type: 'text', placeholder: 'VD: 16GB DDR5' },
    { key: 'warrantyMonth', label: 'Thời hạn bảo hành', type: 'number', unit: 'tháng', defaultValue: 36 },
    { key: 'preInstalledOS', label: 'Hệ điều hành cài sẵn', type: 'text', defaultValue: 'Windows 11 Pro' },
  ],
  cpu: [
    { key: 'socket', label: 'Chuẩn Socket', type: 'select', options: ['LGA1700', 'LGA1851', 'AM5', 'AM4'] },
    { key: 'tdp', label: 'Công suất tỏa nhiệt (TDP)', type: 'number', unit: 'W', placeholder: 'VD: 65, 125' },
    { key: 'igpu', label: 'Đồ họa tích hợp (iGPU)', type: 'boolean' },
  ],
  mainboard: [
    { key: 'socket', label: 'Socket hỗ trợ', type: 'select', options: ['LGA1700', 'LGA1851', 'AM5', 'AM4'] },
    { key: 'ramType', label: 'Chuẩn RAM hỗ trợ', type: 'select', options: ['DDR4', 'DDR5'] },
    { key: 'form', label: 'Kích thước (Form Factor)', type: 'select', options: ['mATX', 'ATX', 'ITX'] },
    { key: 'm2', label: 'Hỗ trợ khe SSD M.2 NVMe', type: 'boolean' },
  ],
  ram: [
    { key: 'ramType', label: 'Chuẩn bộ nhớ RAM', type: 'select', options: ['DDR4', 'DDR5'] },
    { key: 'capacity', label: 'Dung lượng', type: 'select', options: ['8GB', '16GB', '32GB', '64GB'] },
    { key: 'bus', label: 'Tốc độ Bus', type: 'number', unit: 'MHz', placeholder: 'VD: 3200, 5600, 6000' },
  ],
  vga: [
    { key: 'tdp', label: 'Công suất tiêu thụ (TDP)', type: 'number', unit: 'W', placeholder: 'VD: 115, 220' },
    { key: 'length', label: 'Chiều dài Card', type: 'number', unit: 'mm', placeholder: 'VD: 242, 300' },
    { key: 'vram', label: 'Dung lượng VRAM', type: 'select', options: ['8GB GDDR6', '12GB GDDR6X', '16GB GDDR6X'] },
  ],
  ssd: [
    { key: 'nvme', label: 'Chuẩn giao tiếp M.2 NVMe', type: 'boolean' },
    { key: 'capacity', label: 'Dung lượng lưu trữ', type: 'select', options: ['256GB', '500GB', '1TB', '2TB'] },
    { key: 'interface', label: 'Chuẩn kết nối', type: 'select', options: ['PCIe Gen3 x4', 'PCIe Gen4 x4', 'PCIe Gen5 x4', 'SATA III'] },
  ],
  psu: [
    { key: 'watt', label: 'Công suất định danh', type: 'number', unit: 'W', placeholder: 'VD: 550, 650, 750' },
    { key: 'efficiency', label: 'Chứng nhận hiệu suất', type: 'select', options: ['80+ White', '80+ Bronze', '80+ Gold', '80+ Platinum'] },
  ],
  case: [
    { key: 'forms', label: 'Mainboard tương thích', type: 'multiselect', options: ['ITX', 'mATX', 'ATX'] },
    { key: 'maxGpu', label: 'Chiều dài VGA tối đa', type: 'number', unit: 'mm', placeholder: 'VD: 300, 365' },
    { key: 'maxCooler', label: 'Chiều cao tản CPU tối đa', type: 'number', unit: 'mm', placeholder: 'VD: 160, 175' },
  ],
  cooler: [
    { key: 'height', label: 'Chiều cao tản nhiệt', type: 'number', unit: 'mm', placeholder: 'VD: 155, 165' },
    { key: 'sockets', label: 'Socket tương thích', type: 'multiselect', options: ['LGA1700', 'AM5', 'AM4'] },
  ],
  monitor: [
    { key: 'size', label: 'Kích thước màn hình', type: 'select', options: ['24 inch', '27 inch', '32 inch'] },
    { key: 'refreshRate', label: 'Tần số quét', type: 'select', options: ['75Hz', '100Hz', '144Hz', '180Hz', '240Hz'] },
    { key: 'panel', label: 'Tấm nền', type: 'select', options: ['IPS', 'VA', 'OLED'] },
  ],
};
export function formatProductAttrs(cat, attrs = {}) {
  const schema = CATEGORY_ATTR_SCHEMA[cat] || [];
  const rows = [];

  schema.forEach((field) => {
    const val = attrs[field.key];
    if (val === undefined || val === null || val === '') return;

    let displayVal = val;
    if (field.type === 'boolean') {
      displayVal = val ? 'Có hỗ trợ' : 'Không';
    } else if (Array.isArray(val)) {
      displayVal = val.join(', ');
    } else if (field.unit) {
      displayVal = `${val} ${field.unit}`;
    }

    rows.push({ key: field.key, label: field.label, value: displayVal });
  });

  return rows;
}
export const catIcon = (key) => CATEGORIES.find((c) => c.key === key)?.icon || '📦';
export const catLabel = (key) => CATEGORIES.find((c) => c.key === key)?.label || key;
export const getCategoryTree = () => {
  try {
    const saved = localStorage.getItem('cs_category_tree');
    return saved ? JSON.parse(saved) : INITIAL_CATEGORY_TREE;
  } catch {
    return INITIAL_CATEGORY_TREE;
  }
};
export const CATEGORIES = getCategoryTree().flatMap((root) =>
  root.children.map((child) => ({
    key: child.id,
    label: child.name,
    icon: child.icon || root.icon || '📦',
    parentId: root.id,
    parentName: root.name,
    subCategories: child.subCategories || [],
  }))
);
export const saveCategoryTree = (tree) => {
  localStorage.setItem('cs_category_tree', JSON.stringify(tree));
};
// [id, cat, tên, hãng, giá, giá cũ, [thông số hiển thị], {thuộc tính kiểm tra tương thích}]
const RAW = [
[
    'pc1',
    'prebuildpc',
    'PC Gaming Titan CS-01 (i5 14400F / RTX 4060 / 16GB DDR5 / 500GB)',
    'CS Built',
    18890000,
    20990000,
    ['Core i5-14400F', 'RTX 4060 8GB', '16GB DDR5', 'SSD 500GB NVMe', 'Nguồn 650W'],
    { isPrebuilt: true, warrantyMonth: 36, preInstalledOS: 'Windows 11 Pro' }
  ],
  [
    'pc2',
    'prebuildpc',
    'PC Đồ Họa Master CS-02 (Ryzen 7 7800X3D / RTX 4070 Super / 32GB DDR5 / 1TB)',
    'CS Built',
    42990000,
    46500000,
    ['Ryzen 7 7800X3D', 'RTX 4070 Super 12GB', '32GB DDR5 6000MHz', 'SSD 1TB Gen4', 'Nguồn 750W Gold'],
    { isPrebuilt: true, warrantyMonth: 36, preInstalledOS: 'Windows 11 Pro' }
  ],
  [
    'pc3',
    'prebuildpc',
    'PC Văn Phòng – Kế Toán Eco CS-03 (Ryzen 5 7600 / 16GB DDR5 / 500GB)',
    'CS Built',
    13490000,
    14990000,
    ['Ryzen 5 7600 (có iGPU)', '16GB DDR5', 'SSD 500GB NVMe', 'Nguồn 550W Bronze', 'Vỏ mATX nhỏ gọn'],
    { isPrebuilt: true, warrantyMonth: 24, preInstalledOS: 'Windows 11 Pro' }
  ],
  ['c1', 'cpu', 'Intel Core i5-14400F', 'Intel', 5690000, 6490000, ['10 nhân 16 luồng', '4.7 GHz', 'LGA1700'], { socket: 'LGA1700', tdp: 65, igpu: false }],
  ['c2', 'cpu', 'Intel Core i7-14700K', 'Intel', 10990000, 12490000, ['20 nhân 28 luồng', '5.6 GHz', 'LGA1700'], { socket: 'LGA1700', tdp: 125, igpu: true }],
  ['c3', 'cpu', 'AMD Ryzen 5 7600', 'AMD', 5490000, 6290000, ['6 nhân 12 luồng', '5.1 GHz', 'AM5'], { socket: 'AM5', tdp: 65, igpu: true }],
  ['c4', 'cpu', 'AMD Ryzen 7 7800X3D', 'AMD', 11490000, 12990000, ['8 nhân 16 luồng', '96MB cache', 'AM5'], { socket: 'AM5', tdp: 120, igpu: true }],

  ['m1', 'mainboard', 'MSI PRO B760M-E DDR4', 'MSI', 2390000, 2690000, ['LGA1700', 'DDR4', 'mATX'], { socket: 'LGA1700', ramType: 'DDR4', form: 'mATX', m2: true }],
  ['m2', 'mainboard', 'GIGABYTE B760M AORUS ELITE', 'Gigabyte', 3790000, 4190000, ['LGA1700', 'DDR5', 'mATX'], { socket: 'LGA1700', ramType: 'DDR5', form: 'mATX', m2: true }],
  ['m3', 'mainboard', 'ASUS TUF GAMING B650M-PLUS', 'ASUS', 4390000, 4890000, ['AM5', 'DDR5', 'mATX'], { socket: 'AM5', ramType: 'DDR5', form: 'mATX', m2: true }],
  ['m4', 'mainboard', 'MSI MAG Z790 TOMAHAWK WIFI', 'MSI', 6990000, 7790000, ['LGA1700', 'DDR5', 'ATX', 'WiFi 6E'], { socket: 'LGA1700', ramType: 'DDR5', form: 'ATX', m2: true }],

  ['r1', 'ram', 'Kingston Fury Beast 16GB DDR4 3200', 'Kingston', 990000, 1190000, ['16GB', 'DDR4', '3200MHz'], { ramType: 'DDR4' }],
  ['r2', 'ram', 'Corsair Vengeance 16GB (2x8) DDR5 5600', 'Corsair', 1490000, 1790000, ['16GB', 'DDR5', '5600MHz'], { ramType: 'DDR5' }],
  ['r3', 'ram', 'G.Skill Trident Z5 32GB (2x16) DDR5 6000', 'G.Skill', 2990000, 3490000, ['32GB', 'DDR5', '6000MHz'], { ramType: 'DDR5' }],

  ['g1', 'vga', 'MSI GeForce RTX 4060 VENTUS 2X 8GB', 'MSI', 7990000, 8790000, ['8GB GDDR6', 'DLSS 3', '115W'], { tdp: 115, length: 242 }],
  ['g2', 'vga', 'GIGABYTE RTX 4070 SUPER WINDFORCE 12GB', 'Gigabyte', 16990000, 17990000, ['12GB GDDR6X', 'DLSS 3', '220W'], { tdp: 220, length: 261 }],
  ['g3', 'vga', 'Sapphire Radeon RX 7600 PULSE 8GB', 'Sapphire', 6490000, 6990000, ['8GB GDDR6', 'FSR 3', '165W'], { tdp: 165, length: 240 }],

  ['s1', 'ssd', 'Samsung 980 NVMe 500GB', 'Samsung', 1190000, 1390000, ['500GB', 'NVMe Gen3', '3100MB/s'], { nvme: true }],
  ['s2', 'ssd', 'WD Black SN770 1TB', 'WD', 1990000, 2390000, ['1TB', 'NVMe Gen4', '5150MB/s'], { nvme: true }],
  ['s3', 'ssd', 'Kingston NV2 2TB', 'Kingston', 3290000, 3690000, ['2TB', 'NVMe Gen4', '3500MB/s'], { nvme: true }],

  ['p1', 'psu', 'Cooler Master MWE 550W 80+ Bronze', 'Cooler Master', 1190000, 1390000, ['550W', '80+ Bronze'], { watt: 550 }],
  ['p2', 'psu', 'Corsair CV650 650W 80+ Bronze', 'Corsair', 1490000, 1690000, ['650W', '80+ Bronze'], { watt: 650 }],
  ['p3', 'psu', 'MSI MAG A750GL 750W 80+ Gold', 'MSI', 2490000, 2790000, ['750W', '80+ Gold', 'PCIe 5.0'], { watt: 750 }],

  ['k1', 'case', 'Xigmatek Gaming X mATX', 'Xigmatek', 790000, 990000, ['mATX', 'Kính cường lực', '3 fan'], { forms: ['mATX'], maxGpu: 300, maxCooler: 160 }],
  ['k2', 'case', 'NZXT H5 Flow', 'NZXT', 2290000, 2590000, ['ATX', 'Mesh thoáng', '2 fan'], { forms: ['mATX', 'ATX'], maxGpu: 365, maxCooler: 165 }],
  ['k3', 'case', 'Lian Li LANCOOL 216', 'Lian Li', 2990000, 3290000, ['ATX', 'Airflow', '2 fan 160mm'], { forms: ['mATX', 'ATX'], maxGpu: 392, maxCooler: 180 }],

  ['t1', 'cooler', 'Deepcool AK400', 'Deepcool', 690000, 790000, ['Tản khí', '1 tháp', 'Cao 155mm'], { height: 155, sockets: ['LGA1700', 'AM5'] }],
  ['t2', 'cooler', 'Thermalright Peerless Assassin 120 SE', 'Thermalright', 790000, 890000, ['Tản khí', '2 ống đồng', 'Cao 155mm'], { height: 155, sockets: ['LGA1700', 'AM5'] }],
  ['t3', 'cooler', 'Noctua NH-D15', 'Noctua', 2990000, 3290000, ['Tản khí', '2 tháp', 'Cao 165mm'], { height: 165, sockets: ['LGA1700', 'AM5'] }],

  ['d1', 'monitor', 'LG UltraGear 27GS60F 27" 180Hz', 'LG', 4990000, 5990000, ['27"', 'IPS 180Hz', '1ms'], {}],
  ['d2', 'monitor', 'Dell S2425H 24" 100Hz', 'Dell', 2690000, 3190000, ['24"', 'IPS 100Hz', 'Loa tích hợp'], {}],
];

// Đánh giá / lượt bán giả lập ổn định theo id (chỉ để minh họa giao diện)
const seed = (id) => [...id].reduce((s, ch) => s + ch.charCodeAt(0), 0);

export const PRODUCTS = RAW.map(([id, cat, name, brand, price, oldPrice, specs, attrs]) => ({
  id, cat, name, brand, price, oldPrice, specs, attrs,
  icon: catIcon(cat),
  rating: (4.4 + (seed(id) % 6) / 10).toFixed(1),
  sold: 40 + (seed(id) * 17) % 900,
  stock: 3 + (seed(id) % 20),
}));

export const byId = (id) => PRODUCTS.find((p) => p.id === id);
export const discountPercent = (p) => (p.oldPrice > p.price ? Math.round((1 - p.price / p.oldPrice) * 100) : 0);

// ---------- Kiểm tra tương thích cho Build PC (FR-15) ----------
export const BUILD_SLOTS = ['cpu', 'mainboard', 'ram', 'vga', 'ssd', 'psu', 'case', 'cooler'];

/** sel = { cpu: product, mainboard: product, ... } -> danh sách cảnh báo [{cats, text}] */
export function checkCompat(sel) {
  const out = [];
  const { cpu, mainboard: mb, ram, vga, ssd, psu, case: pcCase, cooler } = sel;

  if (cpu && mb && cpu.attrs.socket !== mb.attrs.socket)
    out.push({ cats: ['cpu', 'mainboard'], text: `CPU dùng socket ${cpu.attrs.socket} nhưng mainboard là ${mb.attrs.socket}.` });
  if (ram && mb && ram.attrs.ramType !== mb.attrs.ramType)
    out.push({ cats: ['ram', 'mainboard'], text: `RAM ${ram.attrs.ramType} không dùng được với mainboard ${mb.attrs.ramType}.` });
  if (mb && pcCase && !pcCase.attrs.forms.includes(mb.attrs.form))
    out.push({ cats: ['mainboard', 'case'], text: `Case không hỗ trợ mainboard cỡ ${mb.attrs.form}.` });
  if (vga && pcCase && vga.attrs.length > pcCase.attrs.maxGpu)
    out.push({ cats: ['vga', 'case'], text: `Card đồ họa dài ${vga.attrs.length}mm, case chỉ nhận tối đa ${pcCase.attrs.maxGpu}mm.` });
  if (cooler && pcCase && cooler.attrs.height > pcCase.attrs.maxCooler)
    out.push({ cats: ['cooler', 'case'], text: `Tản nhiệt cao ${cooler.attrs.height}mm, case chỉ nhận tối đa ${pcCase.attrs.maxCooler}mm.` });
  if (cooler && cpu && !cooler.attrs.sockets.includes(cpu.attrs.socket))
    out.push({ cats: ['cooler', 'cpu'], text: `Tản nhiệt không hỗ trợ socket ${cpu.attrs.socket}.` });
  if (ssd && mb && ssd.attrs.nvme && !mb.attrs.m2)
    out.push({ cats: ['ssd', 'mainboard'], text: 'Mainboard không có khe M.2 cho SSD NVMe.' });
  if (cpu && !vga && !cpu.attrs.igpu)
    out.push({ cats: ['cpu', 'vga'], text: `${cpu.name} không có đồ họa tích hợp, cần thêm card đồ họa để xuất hình.` });

  if (psu && (cpu || vga)) {
    const need = Math.ceil(((cpu?.attrs.tdp || 0) + (vga?.attrs.tdp || 0) + 80) * 1.3);
    if (psu.attrs.watt < need)
      out.push({ cats: ['psu', 'cpu', 'vga'], text: `Nguồn ${psu.attrs.watt}W chưa đủ, nên dùng từ ${need}W trở lên.` });
  }
  return out;
}

export const PRESETS = [
  { key: 'office', label: '💼 Văn phòng', parts: ['c3', 'm3', 'r2', 's1', 'p1', 'k1', 't1'] },
  { key: 'gaming', label: '🎮 Gaming', parts: ['c4', 'm3', 'r3', 'g2', 's2', 'p3', 'k2', 't1'] },
  { key: 'creator', label: '🎨 Đồ họa / Stream', parts: ['c2', 'm4', 'r3', 'g2', 's3', 'p3', 'k3', 't2'] },
];
