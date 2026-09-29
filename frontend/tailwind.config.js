/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#F5F7FA', 100: '#E4E7EB', 200: '#CBD2D9', 300: '#9AA5B1', 400: '#7B8794',
          500: '#111827', 600: '#0F172A', 700: '#0B0F19', 800: '#000000', 900: '#000000',
        },
        accent: {
          50: '#eff6ff', 100: '#dbeafe', 200: '#bfdbfe', 300: '#93c5fd', 400: '#60a5fa',
          500: '#3b82f6', 600: '#2563eb', 700: '#1d4ed8', 800: '#1e40af', 900: '#1e3a8a',
        },
        slate: {
          50: '#F9FAFB', 100: '#F3F4F6', 200: '#E5E7EB', 300: '#D1D5DB', 400: '#9CA3AF',
          500: '#6B7280', 600: '#4B5563', 700: '#374151', 800: '#1F2937', 900: '#111827',
        }
      },
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Noto Sans', 'sans-serif'],
      },
      boxShadow: {
        'xs': '0 1px 2px 0 rgba(0, 0, 0, 0.03)',
        'sm': '0 1px 2px 0 rgba(0, 0, 0, 0.04), 0 1px 3px 0 rgba(0, 0, 0, 0.02)',
        DEFAULT: '0 1px 3px 0 rgba(0, 0, 0, 0.05), 0 1px 2px -1px rgba(0, 0, 0, 0.03)',
        'md': '0 4px 6px -1px rgba(0, 0, 0, 0.06), 0 2px 4px -2px rgba(0, 0, 0, 0.02)',
        'lg': '0 10px 15px -3px rgba(0, 0, 0, 0.08), 0 4px 6px -4px rgba(0, 0, 0, 0.04)',
        'xl': '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.05)',
        '2xl': '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
        'inner': 'inset 0 2px 4px 0 rgba(0, 0, 0, 0.02)',
        'premium': '0px 2px 8px -2px rgba(0, 0, 0, 0.05), 0px 4px 12px -2px rgba(0, 0, 0, 0.06)',
        'glass': '0 4px 30px rgba(0, 0, 0, 0.05)',
      },
      keyframes: {
        pulseRing: { '0%': { boxShadow: '0 0 0 0 rgba(17,24,39,.45)' }, '100%': { boxShadow: '0 0 0 10px rgba(17,24,39,0)' } },
        fadeIn: { '0%': { opacity: 0 }, '100%': { opacity: 1 } },
        slideUp: { '0%': { transform: 'translateY(10px)', opacity: 0 }, '100%': { transform: 'translateY(0)', opacity: 1 } },
      },
      animation: { 
        pulseRing: 'pulseRing 1.6s ease-out infinite',
        fadeIn: 'fadeIn 0.4s ease-out forwards',
        slideUp: 'slideUp 0.5s ease-out forwards',
      },
    },
  },
  plugins: [],
}
