// One stroke-based SVG set; presentation only, never permission or module behavior.
const paths = {
  overview: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  categories: 'm12 3 9 5-9 5-9-5 9-5Z M3 12l9 5 9-5 M3 16l9 5 9-5',
  products: 'M3 4h18v13H3z M8 21h8 M12 17v4',
  suppliers: 'M4 21V8l8-5 8 5v13 M2 21h20 M8 10h2 M14 10h2 M8 14h2 M14 14h2 M10 21v-4h4v4',
  customers: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z M2 21v-3a5 5 0 0 1 5-5h4a5 5 0 0 1 5 5v3 M16 4a4 4 0 0 1 0 7 M19 14a5 5 0 0 1 3 4v3',
  inventory: 'm12 3 9 5v9l-9 5-9-5V8l9-5Z M3 8l9 5 9-5 M12 13v9 M7.5 5.5l9 5',
  purchases: 'M12 3v12 m-5-5 5 5 5-5 M4 16v5h16v-5',
  sales: 'M2 3h3l3 13h11l3-9H6 M10 21h.01 M18 21h.01',
  payments: 'M3 5h18v15H3z M3 9h18 M7 15h4',
  returns: 'M8 4 3 9l5 5 M3 9h11a7 7 0 0 1 0 14',
  refunds: 'M3 10a9 9 0 1 1 1 8 M3 4v6h6 M15 8h-4a2 2 0 0 0 0 4h2a2 2 0 0 1 0 4H9 M12 6v12',
  stocktake: 'M9 3h6v4H9z M9 5H5v16h14V5h-4 M8 13l3 3 5-6',
  adjustments: 'M4 3v7 M4 14v7 M12 3v11 M12 18v3 M20 3v3 M20 10v11 M1 10h6 M9 18h6 M17 6h6',
  employees: 'M5 4h14v17H5z M9 4V2h6v2 M12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z M8 18a4 4 0 0 1 8 0',
  reports: 'M3 3v18h18 M7 17v-5 M12 17V7 M17 17V4',
  'account-security': 'M6 10h12v11H6z M8 10V7a4 4 0 0 1 8 0v3 M12 14v3',
  menu: 'M4 6h16 M4 12h16 M4 18h16',
}

export function NavigationIcon({ name }) {
  return <svg className="csm-nav-icon" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d={paths[name]} /></svg>
}
