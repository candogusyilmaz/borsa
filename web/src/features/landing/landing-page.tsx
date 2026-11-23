import { Trans, useLingui } from '@lingui/react/macro';
import {
  IconArrowRight,
  IconBrandLinkedin,
  IconBrandTwitter,
  IconChartLine,
  IconCheck,
  IconCoins,
  IconMail,
  IconPalette,
  IconTrendingUp
} from '@tabler/icons-react';
import { useNavigate } from '@tanstack/react-router';
import { motion } from 'framer-motion';
import { useRef, useState } from 'react';
import { BrandLogo } from '~/shared/components/brand-logo';
import { useFormat } from '~/shared/format/use-format';
import { LanguageSelect } from '~/shared/i18n/language-select';
import styles from './landing.module.css';

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
            ? `radial-gradient(400px circle at ${mousePos.x}px ${mousePos.y}px, color-mix(in srgb, ${color} 8%, transparent), transparent 40%)`
            : 'transparent'
        }}
      />

      <div
        style={{
          position: 'relative',
          background: 'var(--mantine-color-default)',
          backdropFilter: 'blur(12px)',
          border: '1px solid var(--app-border-subtle)',
          borderRadius: '1rem',
          padding: '1.5rem',
          height: '100%',
          overflow: 'hidden',
          transition: 'all 0.3s ease'
        }}>
        {/* Icon */}
        <div
          className={styles['icon-box']}
          style={{
            background: `linear-gradient(135deg, color-mix(in srgb, ${color} 19%, transparent), color-mix(in srgb, ${color} 6%, transparent))`
          }}>
          <Icon size={24} style={{ color }} />
        </div>

        {/* Content */}
        <h3 style={{ fontSize: '1.125rem', fontWeight: 600, color: 'var(--mantine-color-text)', marginBottom: '0.5rem' }}>{title}</h3>
        <p
          style={{
            color: 'var(--mantine-color-dimmed)',
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
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  fontSize: '0.875rem',
                  color: 'var(--mantine-color-dimmed)'
                }}>
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
            background: `linear-gradient(135deg, color-mix(in srgb, ${color} 6%, transparent), transparent)`,
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
  const { t } = useLingui();
  const format = useFormat();
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
          <h4 style={{ color: 'var(--mantine-color-text)', fontWeight: 600, margin: 0 }}>
            <Trans>Portfolio Overview</Trans>
          </h4>
          <p style={{ color: 'var(--mantine-color-dimmed)', fontSize: '0.875rem', margin: 0 }}>
            <Trans>Your investment summary</Trans>
          </p>
        </div>
      </div>

      {/* Stats Row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '1rem', marginBottom: '1.5rem' }}>
        {[
          { label: t`Total Balance`, value: 124532, change: 2.4, positive: true },
          { label: t`Today's P&L`, value: 1245, change: 1.2, positive: true },
          { label: t`Monthly P&L`, value: 8432, change: 7.2, positive: true }
        ].map((stat, i) => (
          <motion.div
            key={stat.label}
            style={{ background: 'var(--mantine-color-default)', borderRadius: '0.75rem', padding: '1rem' }}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 + i * 0.1 }}>
            <p style={{ color: 'var(--mantine-color-dimmed)', fontSize: '0.875rem', marginBottom: '0.25rem' }}>{stat.label}</p>
            <p style={{ color: 'var(--mantine-color-text)', fontWeight: 600, fontSize: '1.125rem', marginBottom: '0.25rem' }}>
              {format.toCurrency(stat.value, false, 'USD')}
            </p>
            <p
              style={{
                fontSize: '0.875rem',
                color: stat.positive ? 'var(--app-finance-profit)' : 'var(--app-finance-loss)'
              }}>
              {format.toLocalePercentage(stat.change, 1, { signDisplay: 'exceptZero' })}
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
              background: 'linear-gradient(to top, var(--mantine-color-brand-4), var(--mantine-primary-color-light))',
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
          { name: t`Stocks`, value: 45, color: 'var(--mantine-color-brand-6)' },
          { name: t`Crypto`, value: 25, color: 'var(--mantine-color-yellow-6)' },
          { name: t`Forex`, value: 20, color: 'var(--mantine-color-violet-6)' },
          { name: t`Metals`, value: 10, color: 'var(--mantine-color-pink-6)' }
        ].map((item, i) => (
          <motion.div
            key={item.name}
            style={{ background: 'var(--mantine-color-default)', borderRadius: '0.5rem', padding: '0.75rem' }}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 1 + i * 0.1 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.25rem' }}>
              <div style={{ width: '0.5rem', height: '0.5rem', borderRadius: '50%', background: item.color }} />
              <span style={{ color: 'var(--mantine-color-dimmed)', fontSize: '0.75rem' }}>{item.name}</span>
            </div>
            <span style={{ color: 'var(--mantine-color-text)', fontWeight: 600 }}>{format.toLocalePercentage(item.value, 0)}</span>
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
  price?: number;
  description: string;
  features: string[];
  highlighted?: boolean;
  badge?: string;
  delay: number;
}) {
  const { t } = useLingui();
  const format = useFormat();
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
        background: highlighted
          ? 'linear-gradient(to bottom, var(--mantine-primary-color-light), transparent)'
          : 'var(--mantine-color-default)',
        border: highlighted ? '2px solid var(--mantine-color-brand-5)' : '1px solid var(--app-border-subtle)'
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
            background: 'var(--mantine-primary-color-filled)',
            borderRadius: '9999px',
            fontSize: '0.875rem',
            fontWeight: 500,
            color: 'var(--mantine-primary-color-contrast)'
          }}>
          {badge}
        </div>
      )}

      <h3 style={{ fontSize: '1.125rem', fontWeight: 600, color: 'var(--mantine-color-text)', marginBottom: '0.25rem' }}>{title}</h3>
      <p style={{ color: 'var(--mantine-color-dimmed)', fontSize: '0.875rem', marginBottom: '1rem' }}>{description}</p>

      <div style={{ marginBottom: '1.5rem' }}>
        <span style={{ fontSize: '2.25rem', fontWeight: 700, color: 'var(--mantine-color-text)' }}>
          {price == null ? t`Free` : format.toCurrency(price, false, 'USD')}
        </span>
        {price != null && (
          <span style={{ color: 'var(--mantine-color-dimmed)', fontSize: '0.875rem' }}>
            <Trans>/month</Trans>
          </span>
        )}
      </div>

      <ul style={{ padding: 0, margin: '0 0 1.5rem 0', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {features.map((feature, _i) => (
          <li
            key={feature}
            style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem', color: 'var(--mantine-color-dimmed)' }}>
            <IconCheck size={16} color="var(--mantine-color-anchor)" />
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
          backgroundColor: highlighted ? 'var(--mantine-primary-color-filled)' : 'var(--app-border-subtle)',
          color: 'var(--mantine-color-text)',
          border: 'none',
          cursor: 'pointer',
          transition: 'background-color 0.2s'
        }}
        whileHover={{ scale: 1.02 }}
        whileTap={{ scale: 0.98 }}
        onClick={navigateToLogin}>
        {price == null ? t`Get Started Free` : t`Coming Soon`}
      </motion.button>
    </motion.div>
  );
}

// Main Component
export function LandingPage() {
  const { t } = useLingui();
  const navigate = useNavigate();

  function navigateToLogin() {
    navigate({ to: '/login' });
  }

  const features = [
    {
      icon: IconChartLine,
      title: t`Trade Management`,
      description: t`Comprehensive tracking for all your trades across multiple portfolios.`,
      features: [t`Buy/Sell tracking`, t`Multi-portfolio support`, t`Multi-currency support`],
      color: 'var(--mantine-color-brand-6)'
    },
    {
      icon: IconTrendingUp,
      title: t`Advanced Analytics`,
      description: t`Deep insights into your portfolio performance with beautiful visualizations.`,
      features: [t`Balance tracking`, t`Daily P&L calculation`, t`Cumulative performance charts`],
      color: 'var(--mantine-color-violet-6)'
    },
    {
      icon: IconCoins,
      title: t`Multiple Instruments`,
      description: t`Support for various asset types including stocks, crypto, forex, and more.`,
      features: [t`Stocks & ETFs`, t`Cryptocurrencies`, t`Forex & Metals`],
      color: 'var(--mantine-color-pink-6)'
    },
    {
      icon: IconPalette,
      title: t`Beautiful UI`,
      description: t`Modern, responsive interface with customizable themes and smooth interactions.`,
      features: [t`Light/Dark mode`, t`Responsive design`, t`Multiple dashboard views`],
      color: 'var(--mantine-color-blue-6)'
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
          background: 'linear-gradient(to bottom right, var(--mantine-color-body), var(--app-surface-secondary), var(--mantine-color-body))'
        }}>
        <FloatingOrb delay={0} size={600} color="var(--mantine-color-brand-6)" initialX={-10} initialY={10} />
        <FloatingOrb delay={2} size={500} color="var(--mantine-color-violet-6)" initialX={60} initialY={50} />
        <FloatingOrb delay={4} size={400} color="var(--mantine-color-pink-6)" initialX={30} initialY={80} />
      </div>

      {/* Content */}
      <div style={{ position: 'relative', zIndex: 10 }}>
        {/* Navigation */}
        <motion.nav className={styles.nav} initial={{ opacity: 0, y: -20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }}>
          <div className={styles['nav-content']}>
            <motion.div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }} whileHover={{ scale: 1.02 }}>
              <BrandLogo />
            </motion.div>

            <div className={styles.navActions}>
              <LanguageSelect />
              <motion.button
                style={{
                  padding: '0.625rem 1.25rem',
                  borderRadius: '0.75rem',
                  background: 'var(--mantine-primary-color-filled)',
                  color: 'var(--mantine-primary-color-contrast)',
                  fontSize: '0.875rem',
                  fontWeight: 500,
                  border: 'none',
                  cursor: 'pointer'
                }}
                whileHover={{ scale: 1.05, boxShadow: '0 0 30px rgba(79, 70, 229, 0.4)' }}
                whileTap={{ scale: 0.95 }}
                onClick={navigateToLogin}>
                <Trans>Get Started</Trans>
              </motion.button>
            </div>
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
                <Trans>
                  Track Your <span className={styles['gradient-text']}>Portfolio</span>
                  <br /> Like Never Before
                </Trans>
              </motion.h1>

              {/* Subtitle */}
              <motion.p className={styles['hero-subtitle']} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.5 }}>
                <Trans>
                  A powerful personal finance and investment tracking platform. Record trades, analyze performance, and gain insights across
                  multiple portfolios.
                </Trans>
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
                  <Trans>Start Free</Trans>
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
                <Trans>
                  Powerful <span style={{ color: 'var(--mantine-color-anchor)' }}>Features</span>
                </Trans>
              </h2>
              <p style={{ color: 'var(--mantine-color-dimmed)', maxWidth: '42rem', margin: '0 auto' }}>
                <Trans>Everything you need to manage your investments and track your trading performance.</Trans>
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
                <Trans>
                  Simple <span style={{ color: 'var(--mantine-color-anchor)' }}>Pricing</span>
                </Trans>
              </h2>
              <p style={{ color: 'var(--mantine-color-dimmed)', maxWidth: '42rem', margin: '0 auto' }}>
                <Trans>Core features are completely free. AI-powered features available as optional add-ons.</Trans>
              </p>
            </motion.div>

            <div className={styles['price-grid']}>
              <PricingCard
                title={t`Free Forever`}
                description={t`All core features, no credit card required.`}
                features={[
                  t`Unlimited portfolios`,
                  t`All trade management features`,
                  t`Full analytics dashboard`,
                  t`Multi-currency support`,
                  t`Light/Dark mode`
                ]}
                highlighted
                badge={t`Most Popular`}
                delay={0.1}
              />
              <PricingCard
                title={t`AI Power Pack`}
                price={4.99}
                description={t`AI-enhanced features coming soon.`}
                features={[
                  t`Everything in Free`,
                  t`AI trade insights`,
                  t`Smart portfolio suggestions`,
                  t`Automated risk analysis`,
                  t`AI-assisted data enrichment`
                ]}
                delay={0.2}
              />
            </div>

            <motion.p
              style={{
                textAlign: 'center',
                color: 'var(--mantine-color-dimmed)',
                fontSize: '0.875rem',
                marginTop: '2rem'
              }}
              initial={{ opacity: 0 }}
              whileInView={{ opacity: 1 }}
              viewport={{ once: true }}
              transition={{ delay: 0.4 }}>
              <Trans>Cancel anytime • No hidden fees</Trans>
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
              <Trans>
                Ready to <span style={{ color: 'var(--mantine-color-anchor)' }}>Transform</span> Your Trading?
              </Trans>
            </h2>
            <p
              style={{
                color: 'var(--mantine-color-dimmed)',
                fontSize: '1.125rem',
                marginBottom: '2rem',
                maxWidth: '42rem',
                margin: '0 auto 2rem auto'
              }}>
              <Trans>Start tracking your investments and gain valuable insights into your trading performance.</Trans>
            </p>
            <motion.button
              style={{
                padding: '1.25rem 2.5rem',
                borderRadius: '0.75rem',
                background: 'linear-gradient(to right, var(--mantine-color-brand-7), var(--mantine-primary-color-filled))',
                color: 'var(--mantine-primary-color-contrast)',
                fontSize: '1.125rem',
                fontWeight: 500,
                border: 'none',
                cursor: 'pointer'
              }}
              whileHover={{ scale: 1.05, boxShadow: '0 0 50px rgba(79, 70, 229, 0.5)' }}
              whileTap={{ scale: 0.95 }}
              onClick={navigateToLogin}>
              <Trans>Get Started Free</Trans>
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
                <BrandLogo />
                <p style={{ color: 'var(--mantine-color-dimmed)', fontSize: '0.875rem' }}>
                  <Trans>A personal portfolio and analytics platform for traders and investors.</Trans>
                </p>
              </div>

              {/* Links */}
              {[
                {
                  title: t`Product`,
                  links: [t`Features`, t`Pricing`]
                },
                {
                  title: t`Resources`,
                  links: [t`Documentation`, t`API Reference`]
                },
                {
                  title: t`Legal`,
                  links: [t`Privacy Policy`, t`Terms of Service`]
                }
              ].map((section) => (
                <div key={section.title}>
                  <h4 style={{ fontWeight: 600, color: 'var(--mantine-color-text)', marginBottom: '1rem' }}>{section.title}</h4>
                  <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    {section.links.map((link) => (
                      <li key={link}>
                        <a
                          // biome-ignore lint/a11y/useValidAnchor: Links are placeholders and will be updated with real URLs in the future.
                          href="#"
                          style={{
                            color: 'var(--mantine-color-dimmed)',
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
                borderTop: '1px solid var(--app-border-subtle)',
                paddingTop: '2rem',
                display: 'flex',
                flexDirection: 'column',
                gap: '1rem',
                alignItems: 'center',
                justifyContent: 'space-between'
              }}>
              <p style={{ color: 'var(--mantine-color-dimmed)', fontSize: '0.875rem' }}>
                <Trans>© 2024 Portfolio Analytics. All rights reserved.</Trans>
              </p>
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
                      background: 'var(--mantine-color-default)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: 'var(--mantine-color-dimmed)',
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
