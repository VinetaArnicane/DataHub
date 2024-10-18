import React from 'react';
import styles from './hero.module.scss';
import Link from "@docusaurus/Link";

const Hero = ({ onOpenTourModal, heroContent }) => {
  const { topQuote, title, description, imgSrc } = heroContent
  return (
    <div className={styles.hero}>
      <div className={styles.hero__container} style={{ backgroundImage: `url(/img/solutions/hero-background.png)` }}>
        <div className={styles.hero__topQuote}>
          {topQuote}
        </div>
        <div className={styles.hero__title}>
          {title}
        </div>
        <p className={styles.hero__description}>{description}</p>
        <div className={styles.hero__cta}>
          <Link className={styles.cta__primary} to="/cloud">
            Book a Demo
          </Link>
          <a
            className={styles.cta__secondary}
            onClick={onOpenTourModal}
          >
            Product Tour
          </a>
        </div>
        <Link className={styles.cta__tertiary} to="/docs">
            Get started with Core →
        </Link>
        {/* push image to bottom */}
        <div style={{ flexGrow: 1 }} />
        <img
          src={imgSrc}
          alt="DataHub Platform Preview"
          className={styles.hero__img}
        />
      </div>
    </div>
  );
};

export default Hero;