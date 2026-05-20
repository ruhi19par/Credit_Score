export type UserRole = "BORROWER" | "LENDER" | "ADMIN";
export type EmploymentType = "SALARIED" | "SELF_EMPLOYED" | "BUSINESS" | "STUDENT" | "UNEMPLOYED";
export type RepaymentHistory = "EXCELLENT" | "GOOD" | "AVERAGE" | "POOR" | "NONE";
export type IncomeStability = "STABLE" | "MODERATE" | "UNSTABLE";
export type ApplicationStatus =
  | "SUBMITTED"
  | "WAITING_FOR_DOCUMENTS"
  | "PROCESSING"
  | "SCORED"
  | "UNDER_REVIEW"
  | "APPROVED"
  | "REJECTED"
  | (string & {});
export type ApplicationMode = "BASIC" | "VERIFIED";
export type RiskLevel = "LOW" | "MEDIUM" | "HIGH";
export type DocumentType =
  | "ID_PROOF"
  | "ADDRESS_PROOF"
  | "BANK_STATEMENT"
  | "INCOME_PROOF"
  | "SALARY_SLIP"
  | "EMPLOYMENT_PROOF"
  | "TAX_RETURN"
  | "BUSINESS_REGISTRATION"
  | "GST_FILING"
  | "INVOICE"
  | "SALES_RECORD"
  | "EXISTING_LOAN_STATEMENT"
  | "UTILITY_BILL"
  | "OTHER";
export type DocumentStatus = "UPLOADED" | "PROCESSING" | "PROCESSED" | "FAILED";

export type User = {
  id: number;
  fullName: string;
  email: string;
  role: UserRole;
  createdAt: string;
};

export type LoginResponse = {
  user: User;
  tokenType: string;
  token: string;
};

export type ApplicationSummary = {
  id: number;
  fullName: string;
  mode: ApplicationMode;
  status: ApplicationStatus;
  requestedAmount: number;
  tenureMonths: number;
  createdAt: string;
  userId: number;
  reviewNotes?: string | null;
  reviewedByUserId?: number | null;
  reviewedAt?: string | null;
};

export type BasicApplicationRequest = {
  fullName: string;
  employmentType: EmploymentType;
  monthlyIncome: number;
  monthlyExpenses: number;
  existingDebtPayment: number;
  repaymentHistory: RepaymentHistory;
  incomeStability: IncomeStability;
  requestedAmount: number;
  tenureMonths: number;
};

export type VerifiedApplicationRequest = BasicApplicationRequest;

export type ScoreReport = {
  applicationId: number;
  fullName?: string;
  mode?: ApplicationMode;
  status?: ApplicationStatus;
  requestedAmount?: number;
  tenureMonths?: number;
  employmentType?: EmploymentType;
  monthlyIncome?: number;
  monthlyExpenses?: number;
  existingDebtPayment?: number;
  repaymentHistory?: RepaymentHistory;
  incomeStability?: IncomeStability;
  score?: number | null;
  riskLevel?: RiskLevel | null;
  debtToIncomeRatio?: number | null;
  expenseRatio?: number | null;
  repaymentCapacity?: number | null;
  suggestedLoanLimit?: number | null;
  positiveFactors?: string[];
  riskFactors?: string[];
  riskExplanation?: string | null;
  modelConfidenceScore?: number | null;
  defaultRisk?: string | number | boolean | null;
  lendingRecommendation?: string | null;
  verifiedDocumentCount?: number | null;
  llmModel?: string | null;
  llmPromptVersion?: string | null;
  llmReasoningSummary?: string | null;
  reviewNotes?: string | null;
  reviewedByUserId?: number | null;
  reviewedAt?: string | null;
  cashFlowStabilityScore?: number | null;
  businessHealthScore?: number | null;
  fraudIndicators?: string[];
  createdAt?: string;
};

export type UploadedDocument = {
  id: number;
  applicationId: number;
  documentType: DocumentType;
  originalFilename: string;
  status: DocumentStatus;
  createdAt: string;
};

export type AdminApplication = ApplicationSummary & {
  userEmail: string;
  score: number | null;
  riskLevel: RiskLevel | null;
  riskExplanation?: string | null;
  modelConfidenceScore?: number | null;
  defaultRisk?: string | number | boolean | null;
  lendingRecommendation?: string | null;
  cashFlowStabilityScore?: number | null;
  businessHealthScore?: number | null;
  fraudIndicators?: string[];
};

export type AdminOverview = {
  totalApplications: number;
  totalUsers: number;
  totalScores: number;
  totalRequestedAmount: number;
  averageScore: number;
  applicationsByStatus: Partial<Record<ApplicationStatus, number>>;
  scoresByRiskLevel: Partial<Record<RiskLevel, number>>;
};

export type BackendHealth = {
  service: string;
  status: string;
};

export type PrivacyConsent = {
  id: number;
  applicationId: number;
  userId?: number;
  purpose?: string;
  accepted?: boolean;
  consentType?: string;
  granted?: boolean;
  revoked?: boolean;
  grantedAt?: string;
  revokedAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
};

export type PrivacyAuditEvent = {
  id: number;
  userId?: number | null;
  userEmail?: string | null;
  applicationId?: number | null;
  eventType?: string;
  action?: string;
  details?: string | null;
  createdAt?: string;
};

const API_BASE = import.meta.env.VITE_API_BASE_URL || "";
const TOKEN_KEY = "credbridge_token";
const USER_KEY = "credbridge_user";

export function getStoredToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): User | null {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? JSON.parse(raw) as User : null;
}

export function storeSession(auth: LoginResponse) {
  localStorage.setItem(TOKEN_KEY, auth.token);
  localStorage.setItem(USER_KEY, JSON.stringify(auth.user));
}

export function storeUser(user: User) {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = getStoredToken();

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers
  });

  if (!response.ok) {
    const fallback = `${response.status} ${response.statusText}`;
    const body = await response.text();
    let message = body || fallback;

    try {
      const error = JSON.parse(body);
      message = error.message || error.error || fallback;
    } catch {
      message = body || fallback;
    }

    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

async function requestBlob(path: string, options: RequestInit = {}): Promise<Blob> {
  const headers = new Headers(options.headers);
  const token = getStoredToken();

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers
  });

  if (!response.ok) {
    const fallback = `${response.status} ${response.statusText}`;
    const body = await response.text();
    throw new Error(body || fallback);
  }

  return response.blob();
}

async function health(): Promise<BackendHealth> {
  const token = getStoredToken();
  const headers = new Headers();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE}/api/auth/me`, { headers });
  if (response.status === 401 || response.status === 403 || response.ok) {
    return { service: "credbridge-backend", status: "running" };
  }
  throw new Error(`${response.status} ${response.statusText}`);
}

export const api = {
  health,
  register: (payload: { fullName: string; email: string; password: string; role: UserRole }) =>
    request<User>("/api/auth/register", { method: "POST", body: JSON.stringify(payload) }),
  login: (payload: { email: string; password: string }) =>
    request<LoginResponse>("/api/auth/login", { method: "POST", body: JSON.stringify(payload) }),
  me: () => request<User>("/api/auth/me"),
  listApplications: () => request<ApplicationSummary[]>("/api/applications"),
  updateApplicationStatus: (applicationId: number, status: ApplicationStatus) =>
    request<ApplicationSummary>(`/api/applications/${applicationId}/status`, { method: "PATCH", body: JSON.stringify({ status }) }),
  updateApplicationReview: (applicationId: number, status: ApplicationStatus, reviewNotes: string) =>
    request<ApplicationSummary>(`/api/applications/${applicationId}/status`, { method: "PATCH", body: JSON.stringify({ status, reviewNotes }) }),
  createBasicApplication: (payload: BasicApplicationRequest) =>
    request<ScoreReport>("/api/applications/basic", { method: "POST", body: JSON.stringify(payload) }),
  createVerifiedApplication: (payload: VerifiedApplicationRequest) =>
    request<ApplicationSummary>("/api/applications/verified", { method: "POST", body: JSON.stringify(payload) }),
  getReport: (applicationId: number) => request<ScoreReport>(`/api/reports/${applicationId}`),
  downloadReportPdf: (applicationId: number) => requestBlob(`/api/reports/${applicationId}/pdf`),
  uploadDocument: (applicationId: number, documentType: DocumentType, file: File) => {
    const form = new FormData();
    form.set("applicationId", String(applicationId));
    form.set("documentType", documentType);
    form.set("file", file);
    return request<UploadedDocument>("/api/documents/upload", { method: "POST", body: form });
  },
  listDocuments: (applicationId: number) =>
    request<UploadedDocument[]>(`/api/documents/application/${applicationId}`),
  deleteDocument: (documentId: number) =>
    request<void>(`/api/documents/${documentId}`, { method: "DELETE" }),
  listConsents: () => request<PrivacyConsent[]>("/api/privacy/consents"),
  revokeConsent: (applicationId: number) =>
    request<PrivacyConsent>(`/api/privacy/applications/${applicationId}/consent/revoke`, { method: "PATCH" }),
  auditEvents: () => request<PrivacyAuditEvent[]>("/api/privacy/audit-events"),
  adminOverview: () => request<AdminOverview>("/api/admin/overview"),
  adminApplications: () => request<AdminApplication[]>("/api/admin/applications")
};
