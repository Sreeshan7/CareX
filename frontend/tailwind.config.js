/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#f0fdfa', 100: '#ccfbf1', 200: '#99f6e4', 300: '#5eead4', 400: '#2dd4bf',
          500: '#14b8a6', 600: '#0d9488', 700: '#0f766e', 800: '#115e59', 900: '#134e4a',
        },
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Noto Sans',
          'Noto Sans Tamil', 'Noto Sans Devanagari', 'sans-serif'],
      },
      keyframes: {
        pulseRing: { '0%': { boxShadow: '0 0 0 0 rgba(13,148,136,.45)' }, '100%': { boxShadow: '0 0 0 10px rgba(13,148,136,0)' } },
      },
      animation: { pulseRing: 'pulseRing 1.6s ease-out infinite' },
    },
  },
  plugins: [],
}
