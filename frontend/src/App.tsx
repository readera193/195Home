import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './hooks/useAuth';
import ProtectedRoute from './components/ProtectedRoute';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import HomePage from './pages/HomePage';
import FamilyGroupPage from './pages/FamilyGroupPage';
import MembersPage from './pages/MembersPage';
import PaymentAccountsPage from './pages/PaymentAccountsPage';
import ExpenseFormPage from './pages/ExpenseFormPage';
import ExpenseListPage from './pages/ExpenseListPage';
import StatisticsPage from './pages/StatisticsPage';
import LineBindingPage from './pages/LineBindingPage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            <Route path="/" element={<HomePage />} />
            <Route path="/family" element={<FamilyGroupPage />} />
            <Route path="/members" element={<MembersPage />} />
            <Route path="/accounts" element={<PaymentAccountsPage />} />
            <Route path="/expenses/new" element={<ExpenseFormPage />} />
            <Route path="/expenses" element={<ExpenseListPage />} />
            <Route path="/statistics" element={<StatisticsPage />} />
            <Route path="/line-binding" element={<LineBindingPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
