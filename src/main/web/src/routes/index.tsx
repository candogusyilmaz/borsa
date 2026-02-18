import {
  IconArrowRight,
  IconBrandLinkedin,
  IconBrandTwitter,
  IconChartBar,
  IconChartLine,
  IconCheck,
  IconCoins,
  IconMail,
  IconPalette,
  IconTrendingUp
} from '@tabler/icons-react';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { motion } from 'framer-motion';
import { useRef, useState } from 'react';
import styles from './landing.module.css';

export const Route = createFileRoute('/')({
  component: PortfolioAnalyticsLanding
});

// Floating background orb
const FloatingOrb = ({
  delay,
  size,
  color,
  initialX,
  initialY
}: {
  delay: number;
  size: number;
  color: string;
  initialX: number;
  initialY: number;
}) => (
  <motion.div
    className={styles.orb}
    style={{
      width: size,
      height: size,
      background: `radial-gradient(circle, ${color} 0%, transparent 70%)`,
      left: `${initialX}%`,
      top: `${initialY}%`
    }}
    animate={{
      x: [0, 100, -50, 0],
      y: [0, -80, 100, 0],
      scale: [1, 1.2, 0.8, 1]
    }}
    transition={{
      duration: 25,
      delay,
      repeat: Infinity,
      ease: 'easeInOut'
    }}
  />
);

// Feature Card Component
const FeatureCard = ({
  icon: Icon,
  title,
  description,
  features,
  color,
  delay
}: {
  icon: React.ElementType;
  title: string;
  description: string;
  features?: string[];
  color: string;
  delay: number;
}) => {
  const [isHovered, setIsHovered] = useState(false);
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 });
  const cardRef = useRef<HTMLDivElement>(null);

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!cardRef.current) return;
    const rect = cardRef.current.getBoundingClientRect();
    setMousePos({
      x: e.clientX - rect.left,
      y: e.clientY - rect.top
    });
  };

  return (
    <motion.div
      ref={cardRef}
      style={{ position: 'relative' }}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.5 }}
      onMouseMove={handleMouseMove}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}>
      {/* Spotlight effect */}
      <motion.div
        style={{
          pointerEvents: 'none',
          position: 'absolute',
          inset: 0,
          borderRadius: '1rem',
          zIndex: 10,
          background: isHovered
            ? `radial-gradient(400px circle at ${mousePos.x}px ${mousePos.y}px, ${color}15, transparent 40%)`
            : 'transparent'
        }}
      />

      <div
        style={{
          position: 'relative',
          background: 'rgba(255, 255, 255, 0.05)',
          backdropFilter: 'blur(12px)',
          border: '1px solid rgba(255, 255, 255, 0.1)',
          borderRadius: '1rem',
          padding: '1.5rem',
          height: '100%',
          overflow: 'hidden',
          transition: 'all 0.3s ease'
        }}>
        {/* Icon */}
        <div className={styles['icon-box']} style={{ background: `linear-gradient(135deg, ${color}30, ${color}10)` }}>
          <Icon size={24} style={{ color }} />
        </div>

        {/* Content */}
        <h3 style={{ fontSize: '1.125rem', fontWeight: 600, color: 'white', marginBottom: '0.5rem' }}>{title}</h3>
        <p
          style={{
            color: 'rgba(255, 255, 255, 0.6)',
            fontSize: '0.875rem',
            lineHeight: 1.625,
            marginBottom: '1rem'
          }}>
          {description}
        </p>

        {/* Feature list */}
        {features && (
          <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {features.map((feature, _i) => (
              <li
                key={feature}
                style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem', color: 'rgba(255, 255, 255, 0.5)' }}>
                <IconCheck size={16} style={{ color }} />
                {feature}
              </li>
            ))}
          </ul>
        )}

        {/* Hover glow */}
        <motion.div
          style={{
            position: 'absolute',
            inset: -1,
            borderRadius: '1rem',
            background: `linear-gradient(135deg, ${color}10, transparent)`,
            opacity: isHovered ? 1 : 0,
            transition: 'opacity 0.3s'
          }}
        />
      </div>
    </motion.div>
  );
};

// Mock Dashboard Preview
const DashboardPreview = () => {
  const chartData = [35, 45, 30, 50, 40, 60, 55, 70, 65, 80, 75, 90];

  return (
    <motion.div
      className={styles['dashboard-preview']}
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.8 }}>
      {/* Dashboard Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div>
          <h4 style={{ color: 'white', fontWeight: 600, margin: 0 }}>Portfolio Overview</h4>
          <p style={{ color: 'rgba(255, 255, 255, 0.5)', fontSize: '0.875rem', margin: 0 }}>Your investment summary</p>
        </div>
      </div>

      {/* Stats Row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '1rem', marginBottom: '1.5rem' }}>
        {[
          { label: 'Total Balance', value: '$124,532.00', change: '+2.4%', positive: true },
          { label: "Today's P&L", value: '+$1,245.00', change: '+1.2%', positive: true },
          { label: 'Monthly P&L', value: '+$8,432.00', change: '+7.2%', positive: true }
        ].map((stat, i) => (
          <motion.div
            key={stat.label}
            style={{ background: 'rgba(255, 255, 255, 0.05)', borderRadius: '0.75rem', padding: '1rem' }}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 + i * 0.1 }}>
            <p style={{ color: 'rgba(255, 255, 255, 0.5)', fontSize: '0.875rem', marginBottom: '0.25rem' }}>{stat.label}</p>
            <p style={{ color: 'white', fontWeight: 600, fontSize: '1.125rem', marginBottom: '0.25rem' }}>{stat.value}</p>
            <p
              style={{
                fontSize: '0.875rem',
                color: stat.positive ? '#22c55e' : '#ef4444'
              }}>
              {stat.change}
            </p>
          </motion.div>
        ))}
      </div>

      {/* Chart */}
      <div style={{ height: '10rem', display: 'flex', alignItems: 'flex-end', gap: '0.25rem' }}>
        {chartData.map((height, i) => (
          <motion.div
            // biome-ignore lint/suspicious/noArrayIndexKey: Using index as key is acceptable here because the chartData array is static and does not change order.
            key={i}
            style={{
              flex: 1,
              background: 'linear-gradient(to top, rgba(99, 102, 241, 0.5), rgba(99, 102, 241, 0.1))',
              borderTopLeftRadius: '0.25rem',
              borderTopRightRadius: '0.25rem'
            }}
            initial={{ height: 0 }}
            animate={{ height: `${height}%` }}
            transition={{ delay: 0.5 + i * 0.05, duration: 0.5, ease: 'easeOut' }}
          />
        ))}
      </div>

      {/* Portfolio Breakdown */}
      <div style={{ marginTop: '1.5rem', display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '0.75rem' }}>
        {[
          { name: 'Stocks', value: '45%', color: '#6366f1' },
          { name: 'Crypto', value: '25%', color: '#eab308' },
          { name: 'Forex', value: '20%', color: '#a855f7' },
          { name: 'Metals', value: '10%', color: '#ec4899' }
        ].map((item, i) => (
          <motion.div
            key={item.name}
            style={{ background: 'rgba(255, 255, 255, 0.05)', borderRadius: '0.5rem', padding: '0.75rem' }}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 1 + i * 0.1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
              <div style={{ width: '0.5rem', height: '0.5rem', borderRadius: '50%', background: item.color }} />
              <span style={{ color: 'rgba(255, 255, 255, 0.6)', fontSize: '0.75rem' }}>{item.name}</span>
            </div>
            <span style={{ color: 'white', fontWeight: 600 }}>{item.value}</span>
          </motion.div>
        ))}
      </div>
    </motion.div>
  );
};

// Pricing Card
function PricingCard({
  title,
  price,
  description,
  features,
  highlighted,
  badge,
  delay
}: {
  title: string;
  price: string;
  description: string;
  features: string[];
  highlighted?: boolean;
  badge?: string;
  delay: number;
}) {
  const navigate = useNavigate();

  function navigateToLogin() {
    navigate({ to: '/login' });
  }

  return (
    <motion.div
      style={{
        position: 'relative',
        borderRadius: '1rem',
        padding: '1.5rem',
        background: highlighted ? 'linear-gradient(to bottom, rgba(79, 70, 229, 0.2), transparent)' : 'rgba(255, 255, 255, 0.05)',
        border: highlighted ? '2px solid rgba(79, 70, 229, 0.3)' : '1px solid rgba(255, 255, 255, 0.1)'
      }}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.5 }}>
      {badge && (
        <div
          style={{
            position: 'absolute',
            top: '-0.75rem',
            left: '50%',
            transform: 'translateX(-50%)',
            padding: '0.25rem 1rem',
            background: '#4f46e5',
            borderRadius: '9999px',
            fontSize: '0.875rem',
            fontWeight: 500,
            color: 'white'
          }}>
          {badge}
        </div>
      )}

      <h3 style={{ fontSize: '1.125rem', fontWeight: 600, color: 'white', marginBottom: '0.25rem' }}>{title}</h3>
      <p style={{ color: 'rgba(255, 255, 255, 0.5)', fontSize: '0.875rem', marginBottom: '1rem' }}>{description}</p>

      <div style={{ marginBottom: '1.5rem' }}>
        <span style={{ fontSize: '2.25rem', fontWeight: 700, color: 'white' }}>{price}</span>
        {price !== 'Free' && <span style={{ color: 'rgba(255, 255, 255, 0.5)', fontSize: '0.875rem' }}>/month</span>}
      </div>

      <ul style={{ padding: 0, margin: '0 0 1.5rem 0', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {features.map((feature, _i) => (
          <li
            key={feature}
            style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem', color: 'rgba(255, 255, 255, 0.7)' }}>
            <IconCheck size={16} color="#818cf8" />
            {feature}
          </li>
        ))}
      </ul>

      <motion.button
        style={{
          width: '100%',
          padding: '0.75rem 0',
          borderRadius: '0.75rem',
          fontWeight: 500,
          backgroundColor: highlighted ? '#4f46e5' : 'rgba(255, 255, 255, 0.1)',
          color: 'white',
          border: 'none',
          cursor: 'pointer',
          transition: 'background-color 0.2s'
        }}
        whileHover={{ scale: 1.02 }}
        whileTap={{ scale: 0.98 }}
        onClick={navigateToLogin}>
        {price === 'Free' ? 'Get Started Free' : 'Coming Soon'}
      </motion.button>
    </motion.div>
  );
}

// Main Component
function PortfolioAnalyticsLanding() {
  const navigate = useNavigate();

  function navigateToLogin() {
    navigate({ to: '/login' });
  }

  const features = [
    {
      icon: IconChartLine,
      title: 'Trade Management',
      description: 'Comprehensive tracking for all your trades across multiple portfolios.',
      features: ['Buy/Sell tracking', 'Multi-portfolio support', 'Multi-currency support'],
      color: '#6366f1'
    },
    {
      icon: IconTrendingUp,
      title: 'Advanced Analytics',
      description: 'Deep insights into your portfolio performance with beautiful visualizations.',
      features: ['Balance tracking', 'Daily P&L calculation', 'Cumulative performance charts'],
      color: '#a855f7'
    },
    {
      icon: IconCoins,
      title: 'Multiple Instruments',
      description: 'Support for various asset types including stocks, crypto, forex, and more.',
      features: ['Stocks & ETFs', 'Cryptocurrencies', 'Forex & Metals'],
      color: '#ec4899'
    },
    {
      icon: IconPalette,
      title: 'Beautiful UI',
      description: 'Modern, responsive interface with customizable themes and smooth interactions.',
      features: ['Light/Dark mode', 'Responsive design', 'Multiple dashboard views'],
      color: '#3b82f6'
    }
  ];

  return (
    <div className={styles.container}>
      {/* Animated background */}
      <div
        style={{
          position: 'fixed',
          inset: 0,
          overflow: 'hidden',
          pointerEvents: 'none',
          background: 'linear-gradient(to bottom right, #020617, #0f172a, #020617)'
        }}>
        <FloatingOrb delay={0} size={600} color="#6366f1" initialX={-10} initialY={10} />
        <FloatingOrb delay={2} size={500} color="#a855f7" initialX={60} initialY={50} />
        <FloatingOrb delay={4} size={400} color="#ec4899" initialX={30} initialY={80} />
      </div>

      {/* Content */}
      <div style={{ position: 'relative', zIndex: 10 }}>
        {/* Navigation */}
        <motion.nav className={styles.nav} initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
          <div className={styles['nav-content']}>
            <motion.div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }} whileHover={{ scale: 1.02 }}>
              <div
                style={{
                  width: '2.5rem',
                  height: '2.5rem',
                  borderRadius: '0.75rem',
                  background: 'linear-gradient(to bottom right, #4f46e5, #7c3aed)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}>
                <IconChartBar size={20} color="white" />
              </div>
              <span style={{ fontSize: '1.25rem', fontWeight: 700 }}>
                <span style={{ color: 'white' }}>Portfolio</span>
                <span style={{ color: '#818cf8' }}>Analytics</span>
              </span>
            </motion.div>

            <motion.button
              style={{
                padding: '0.625rem 1.25rem',
                borderRadius: '0.75rem',
                background: '#4f46e5',
                color: 'white',
                fontSize: '0.875rem',
                fontWeight: 500,
                border: 'none',
                cursor: 'pointer'
              }}
              whileHover={{ scale: 1.05, boxShadow: '0 0 30px rgba(79, 70, 229, 0.4)' }}
              whileTap={{ scale: 0.95 }}
              onClick={navigateToLogin}>
              Get Started
            </motion.button>
          </div>
        </motion.nav>

        {/* Hero Section */}
        <section className={styles['hero-section']}>
          <div style={{ maxWidth: '80rem', margin: '0 auto' }}>
            <div style={{ textAlign: 'center', maxWidth: '56rem', margin: '0 auto 4rem auto' }}>
              {/* Title */}
              <motion.h1
                className={styles['hero-title']}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.3, duration: 0.6 }}>
                Track Your <span className={styles['gradient-text']}>Portfolio</span>
                <br />
                Like Never Before
              </motion.h1>

              {/* Subtitle */}
              <motion.p className={styles['hero-subtitle']} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.5 }}>
                A powerful personal finance and investment tracking platform. Record trades, analyze performance, and gain insights across
                multiple portfolios.
              </motion.p>

              {/* CTA Buttons */}
              <motion.div
                style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'center', gap: '1rem' }}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.6 }}>
                <motion.button
                  className={styles['btn-primary']}
                  whileHover={{ scale: 1.05, boxShadow: '0 0 40px rgba(79, 70, 229, 0.4)' }}
                  whileTap={{ scale: 0.95 }}
                  onClick={navigateToLogin}>
                  Start Free
                  <IconArrowRight size={20} />
                </motion.button>
              </motion.div>
            </div>

            {/* Dashboard Preview */}
            <motion.div
              style={{ maxWidth: '64rem', margin: '0 auto' }}
              initial={{ opacity: 0, y: 40 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.8, duration: 0.8 }}>
              <DashboardPreview />
            </motion.div>
          </div>
        </section>

        {/* Features Section */}
        <section id="features" className={styles.section}>
          <div style={{ maxWidth: '80rem', margin: '0 auto' }}>
            <motion.div
              className={styles['section-title']}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}>
              <h2 style={{ fontSize: '2.25rem', fontWeight: 700, marginBottom: '1rem' }}>
                Powerful <span style={{ color: '#818cf8' }}>Features</span>
              </h2>
              <p style={{ color: 'rgba(255, 255, 255, 0.6)', maxWidth: '42rem', margin: '0 auto' }}>
                Everything you need to manage your investments and track your trading performance.
              </p>
            </motion.div>

            <div className={styles['feature-grid']}>
              {features.map((feature, i) => (
                <FeatureCard key={feature.title} {...feature} delay={0.1 * i} />
              ))}
            </div>
          </div>
        </section>

        {/* Pricing Section */}
        <section id="pricing" className={styles.section}>
          <div style={{ maxWidth: '64rem', margin: '0 auto' }}>
            <motion.div
              className={styles['section-title']}
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}>
              <h2 style={{ fontSize: '2.25rem', fontWeight: 700, marginBottom: '1rem' }}>
                Simple <span style={{ color: '#818cf8' }}>Pricing</span>
              </h2>
              <p style={{ color: 'rgba(255, 255, 255, 0.6)', maxWidth: '42rem', margin: '0 auto' }}>
                Core features are completely free. AI-powered features available as optional add-ons.
              </p>
            </motion.div>

            <div className={styles['price-grid']}>
              <PricingCard
                title="Free Forever"
                price="Free"
                description="All core features, no credit card required."
                features={[
                  'Unlimited portfolios',
                  'All trade management features',
                  'Full analytics dashboard',
                  'Multi-currency support',
                  'Light/Dark mode'
                ]}
                highlighted
                badge="Most Popular"
                delay={0.1}
              />
              <PricingCard
                title="AI Power Pack"
                price="$4.99"
                description="AI-enhanced features coming soon."
                features={[
                  'Everything in Free',
                  'AI trade insights',
                  'Smart portfolio suggestions',
                  'Automated risk analysis',
                  'AI-assisted data enrichment'
                ]}
                delay={0.2}
              />
            </div>

            <motion.p
              style={{
                textAlign: 'center',
                color: 'rgba(255, 255, 255, 0.4)',
                fontSize: '0.875rem',
                marginTop: '2rem'
              }}
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true }}
              transition={{ delay: 0.4 }}>
              Cancel anytime • No hidden fees
            </motion.p>
          </div>
        </section>

        {/* CTA Section */}
        <section id="contact" className={styles.section}>
          <motion.div
            style={{ maxWidth: '56rem', margin: '0 auto', textAlign: 'center' }}
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}>
            <h2 style={{ fontSize: '2.25rem', fontWeight: 700, marginBottom: '1.5rem' }}>
              Ready to <span style={{ color: '#818cf8' }}>Transform</span> Your Trading?
            </h2>
            <p
              style={{
                color: 'rgba(255, 255, 255, 0.6)',
                fontSize: '1.125rem',
                marginBottom: '2rem',
                maxWidth: '42rem',
                margin: '0 auto 2rem auto'
              }}>
              Start tracking your investments and gain valuable insights into your trading performance.
            </p>
            <motion.button
              style={{
                padding: '1.25rem 2.5rem',
                borderRadius: '0.75rem',
                background: 'linear-gradient(to right, #4f46e5, #7c3aed)',
                color: 'white',
                fontSize: '1.125rem',
                fontWeight: 500,
                border: 'none',
                cursor: 'pointer'
              }}
              whileHover={{ scale: 1.05, boxShadow: '0 0 50px rgba(79, 70, 229, 0.5)' }}
              whileTap={{ scale: 0.95 }}
              onClick={navigateToLogin}>
              Get Started Free
            </motion.button>
          </motion.div>
        </section>

        {/* Footer */}
        <footer className={styles.footer}>
          <div style={{ maxWidth: '80rem', margin: '0 auto' }}>
            <div
              style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '2rem', marginBottom: '2rem' }}>
              {/* Brand */}
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
                  <div
                    style={{
                      width: '2rem',
                      height: '2rem',
                      borderRadius: '0.5rem',
                      background: 'linear-gradient(to bottom right, #4f46e5, #7c3aed)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center'
                    }}>
                    <IconChartBar size={16} color="white" />
                  </div>
                  <span style={{ fontWeight: 700 }}>PortfolioAnalytics</span>
                </div>
                <p style={{ color: 'rgba(255, 255, 255, 0.5)', fontSize: '0.875rem' }}>
                  A personal portfolio and analytics platform for traders and investors.
                </p>
              </div>

              {/* Links */}
              {[
                {
                  title: 'Product',
                  links: ['Features', 'Pricing']
                },
                {
                  title: 'Resources',
                  links: ['Documentation', 'API Reference']
                },
                {
                  title: 'Legal',
                  links: ['Privacy Policy', 'Terms of Service']
                }
              ].map((section) => (
                <div key={section.title}>
                  <h4 style={{ fontWeight: 600, color: 'white', marginBottom: '1rem' }}>{section.title}</h4>
                  <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {section.links.map((link) => (
                      <li key={link}>
                        <a
                          // biome-ignore lint/a11y/useValidAnchor: Links are placeholders and will be updated with real URLs in the future.
                          href="#"
                          style={{
                            color: 'rgba(255, 255, 255, 0.5)',
                            fontSize: '0.875rem',
                            textDecoration: 'none',
                            transition: 'color 0.2s'
                          }}>
                          {link}
                        </a>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>

            {/* Bottom */}
            <div
              style={{
                borderTop: '1px solid rgba(255, 255, 255, 0.1)',
                paddingTop: '2rem',
                display: 'flex',
                flexDirection: 'column',
                gap: '1rem',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}>
              <p style={{ color: 'rgba(255, 255, 255, 0.4)', fontSize: '0.875rem' }}>© 2024 Portfolio Analytics. All rights reserved.</p>
              <div style={{ display: 'flex', gap: '1rem' }}>
                {[
                  { icon: IconMail, href: 'mailto:candogusyilmaz@gmail.com' },
                  { icon: IconBrandTwitter, href: '#' },
                  { icon: IconBrandLinkedin, href: 'https://www.linkedin.com/in/candogusyilmaz/' }
                ].map(({ icon: Icon, href }, _i) => (
                  <motion.a
                    key={href}
                    href={href}
                    style={{
                      width: '2.5rem',
                      height: '2.5rem',
                      borderRadius: '0.5rem',
                      background: 'rgba(255, 255, 255, 0.05)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: 'rgba(255, 255, 255, 0.6)',
                      transition: 'color 0.2s'
                    }}
                    whileHover={{ scale: 1.1 }}>
                    <Icon size={20} />
                  </motion.a>
                ))}
              </div>
            </div>
          </div>
        </footer>
      </div>
    </div>
  );
}
