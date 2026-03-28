# Bank Settlement Engine - Frontend

## Overview
A modern Vue.js + TypeScript frontend for a bank settlement and reconciliation system.

## Features Implemented

### ✅ **File Upload (P2P)**
- **Multiple file selection** - Select and upload multiple Excel/CSV files
- **Drag & drop interface** - Intuitive file upload with visual feedback
- **File validation** - Type and size validation (Excel, CSV, max 10MB)
- **Progress tracking** - Real-time upload progress with percentage
- **Duplicate detection** - Prevents selecting the same file multiple times
- **Sequential upload** - Files uploaded one by one to match backend API

### ✅ **Reconciliation Dashboard**
- **KPI cards** - Key metrics with trend indicators
- **Interactive status** - Real-time reconciliation status display
- **Recent activity feed** - Activity timeline with status indicators
- **Quick actions** - Navigation buttons for common tasks
- **Loading states** - Skeleton loaders during data fetching

### ✅ **Modern UI/UX**
- **Professional banking theme** - Green primary colors with proper contrast
- **Dark mode support** - Automatic dark/light theme switching
- **Responsive design** - Mobile-first approach with breakpoints
- **Smooth animations** - Fade-in, slide-up, and hover effects
- **Micro-interactions** - Button hover states, card lift effects

### ✅ **Navigation & Layout**
- **Collapsible sidebar** - Space-saving navigation with tooltips
- **Active route highlighting** - Visual indication of current page
- **Breadcrumb-ready structure** - Proper routing hierarchy

## Technical Stack

- **Vue 3** with Composition API
- **TypeScript** for type safety
- **Tailwind CSS v4** with custom design system
- **Lucide icons** for consistent iconography
- **Axios** for HTTP requests
- **Vue Router** for navigation

## API Integration

Frontend communicates with Spring Boot backend on:
- **Frontend**: `http://localhost:5173/`
- **Backend**: `http://localhost:8080/`

### API Endpoints
- `POST /api/p2p/files/upload` - File upload
- `POST /api/p2p/reconciliation/session/start` - Start reconciliation

## Getting Started

1. Install dependencies: `npm install`
2. Start development server: `npm run dev`
3. Open browser: Navigate to `http://localhost:5173/`

## Browser Support

- Chrome/Edge (Recommended)
- Firefox
- Safari
- Mobile browsers (iOS Safari, Chrome Mobile)

## Development Notes

- Uses Tailwind CSS v4 with custom theme variables
- Hot module replacement (HMR) enabled
- ESLint configured for code quality
- TypeScript strict mode enabled
