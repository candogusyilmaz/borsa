INSERT INTO industries (sector_id, name, description, created_at)
VALUES
-- Energy Sector
((SELECT id FROM sectors WHERE name = 'Energy'), 'Oil, Gas & Consumable Fuels',
 'Companies involved in the exploration, production, and refining of oil, gas, and coal.', NOW()),

-- Materials Sector
((SELECT id FROM sectors WHERE name = 'Materials'), 'Chemicals',
 'Companies that produce industrial chemicals, fertilizers, and specialty chemicals.', NOW()),
((SELECT id FROM sectors WHERE name = 'Materials'), 'Construction Materials',
 'Companies that produce building materials such as cement, aggregates, and glass.', NOW()),
((SELECT id FROM sectors WHERE name = 'Materials'), 'Metals & Mining',
 'Companies involved in the extraction and processing of metals and minerals.', NOW()),

-- Industrials Sector
((SELECT id FROM sectors WHERE name = 'Industrials'), 'Aerospace & Defense',
 'Companies that manufacture aircraft, defense equipment, and related components.', NOW()),
((SELECT id FROM sectors WHERE name = 'Industrials'), 'Machinery',
 'Companies that produce industrial machinery and equipment.', NOW()),
((SELECT id FROM sectors WHERE name = 'Industrials'), 'Transportation',
 'Companies involved in logistics, shipping, and transportation services.', NOW()),

-- Consumer Discretionary Sector
((SELECT id FROM sectors WHERE name = 'Consumer Discretionary'), 'Automobiles',
 'Companies that manufacture automobiles and related components.', NOW()),
((SELECT id FROM sectors WHERE name = 'Consumer Discretionary'), 'Consumer Durables & Apparel',
 'Companies that produce durable goods like appliances, apparel, and footwear.', NOW()),
((SELECT id FROM sectors WHERE name = 'Consumer Discretionary'), 'Hotels, Restaurants & Leisure',
 'Companies that provide hospitality and leisure services.', NOW()),

-- Consumer Staples Sector
((SELECT id FROM sectors WHERE name = 'Consumer Staples'), 'Food & Staples Retailing',
 'Companies that operate grocery stores and retail chains for essential goods.', NOW()),
((SELECT id FROM sectors WHERE name = 'Consumer Staples'), 'Food, Beverage & Tobacco',
 'Companies that produce food, beverages, and tobacco products.', NOW()),
((SELECT id FROM sectors WHERE name = 'Consumer Staples'), 'Household & Personal Products',
 'Companies that manufacture household and personal care products.', NOW()),

-- Health Care Sector
((SELECT id FROM sectors WHERE name = 'Health Care'), 'Biotechnology',
 'Companies engaged in the research and development of biotech products.', NOW()),
((SELECT id FROM sectors WHERE name = 'Health Care'), 'Pharmaceuticals',
 'Companies that develop and produce pharmaceutical drugs.', NOW()),
((SELECT id FROM sectors WHERE name = 'Health Care'), 'Health Care Equipment & Services',
 'Companies that manufacture medical devices and provide health care services.', NOW()),

-- Financials Sector
((SELECT id FROM sectors WHERE name = 'Financials'), 'Banks', 'Companies that provide banking and financial services.',
 NOW()),
((SELECT id FROM sectors WHERE name = 'Financials'), 'Insurance',
 'Companies that provide insurance products and services.', NOW()),
((SELECT id FROM sectors WHERE name = 'Financials'), 'Capital Markets',
 'Companies involved in investment banking, asset management, and brokerage services.', NOW()),

-- Information Technology Sector
((SELECT id FROM sectors WHERE name = 'Information Technology'), 'Software & Services',
 'Companies that develop and provide software and IT services.', NOW()),
((SELECT id FROM sectors WHERE name = 'Information Technology'), 'Technology Hardware & Equipment',
 'Companies that manufacture hardware and electronic equipment.', NOW()),
((SELECT id FROM sectors WHERE name = 'Information Technology'), 'Semiconductors & Semiconductor Equipment',
 'Companies that produce semiconductors and related equipment.', NOW()),

-- Communication Services Sector
((SELECT id FROM sectors WHERE name = 'Communication Services'), 'Telecommunication Services',
 'Companies that provide wired and wireless communication services.', NOW()),
((SELECT id FROM sectors WHERE name = 'Communication Services'), 'Media & Entertainment',
 'Companies that produce and distribute media content and entertainment.', NOW()),

-- Utilities Sector
((SELECT id FROM sectors WHERE name = 'Utilities'), 'Electric Utilities',
 'Companies that generate and distribute electricity.', NOW()),
((SELECT id FROM sectors WHERE name = 'Utilities'), 'Gas Utilities', 'Companies that distribute natural gas.', NOW()),
((SELECT id FROM sectors WHERE name = 'Utilities'), 'Water Utilities',
 'Companies that provide water supply and treatment services.', NOW()),

-- Real Estate Sector
((SELECT id FROM sectors WHERE name = 'Real Estate'), 'Equity Real Estate Investment Trusts (REITs)',
 'Companies that own and operate income-generating real estate properties.', NOW()),
((SELECT id FROM sectors WHERE name = 'Real Estate'), 'Real Estate Management & Development',
 'Companies that manage and develop real estate properties.', NOW());