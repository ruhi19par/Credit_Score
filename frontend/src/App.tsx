import { FormEvent, ReactElement, ReactNode, useEffect, useRef, useState } from "react";
import {
  AdminApplication,
  AdminOverview,
  api,
  ApplicationMode,
  ApplicationStatus,
  ApplicationSummary,
  BasicApplicationRequest,
  clearSession,
  DocumentType,
  getStoredToken,
  getStoredUser,
  IncomeStability,
  RepaymentHistory,
  RiskLevel,
  ScoreReport,
  storeUser,
  storeSession,
  UploadedDocument,
  User,
  UserRole,
  VerifiedApplicationRequest,
  PrivacyAuditEvent,
  PrivacyConsent
} from "./api";

const currency = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });

const employmentTypes = ["SALARIED", "SELF_EMPLOYED", "BUSINESS", "STUDENT", "UNEMPLOYED"] as const;
const repaymentHistory = ["EXCELLENT", "GOOD", "AVERAGE", "POOR", "NONE"] as const;
const incomeStability = ["STABLE", "MODERATE", "UNSTABLE"] as const;
const documentTypes = [
  "ID_PROOF",
  "ADDRESS_PROOF",
  "BANK_STATEMENT",
  "INCOME_PROOF",
  "SALARY_SLIP",
  "EMPLOYMENT_PROOF",
  "TAX_RETURN",
  "BUSINESS_REGISTRATION",
  "GST_FILING",
  "INVOICE",
  "SALES_RECORD",
  "EXISTING_LOAN_STATEMENT",
  "UTILITY_BILL",
  "OTHER"
] as const;
const salariedDocuments: DocumentType[] = ["ID_PROOF", "ADDRESS_PROOF", "SALARY_SLIP", "BANK_STATEMENT", "EMPLOYMENT_PROOF"];
const businessDocuments: DocumentType[] = ["ID_PROOF", "ADDRESS_PROOF", "BANK_STATEMENT", "GST_FILING", "INVOICE", "BUSINESS_REGISTRATION", "SALES_RECORD"];
const coreVerifiedDocuments: DocumentType[] = ["ID_PROOF", "ADDRESS_PROOF", "BANK_STATEMENT"];
const borrowerRoutes: Route[] = ["dashboard", "application", "report", "documents", "consent"];
const adminRoutes: Route[] = ["admin"];
const lenderRoutes: Route[] = ["admin"];
const statusSummary: ApplicationStatus[] = ["SUBMITTED", "WAITING_FOR_DOCUMENTS", "PROCESSING", "SCORED", "UNDER_REVIEW", "APPROVED", "REJECTED"];
const riskSummary: RiskLevel[] = ["LOW", "MEDIUM", "HIGH"];
const reviewStatuses: ApplicationStatus[] = ["UNDER_REVIEW", "APPROVED", "REJECTED"];
const allowedDocumentTypes = ["application/pdf", "image/png", "image/jpeg"];
const authRoles: UserRole[] = ["BORROWER", "LENDER", "ADMIN"];
const APPLICATION_MODE_KEY = "credbridge_application_mode";

type Route =
  | "login"
  | "register"
  | "dashboard"
  | "application"
  | "report"
  | "documents"
  | "consent"
  | "admin";

function currentRoute(): Route {
  const hashRoute = window.location.hash.replace(/^#\/?/, "");
  const pathRoute = window.location.pathname.replace(/^\/+/, "");
  const part = (hashRoute || pathRoute) as Route;
  return part || "dashboard";
}

function navigate(path: string) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  window.history.pushState({}, "", `#${normalizedPath}`);
  window.dispatchEvent(new PopStateEvent("popstate"));
}

export default function App() {
  const [route, setRoute] = useState<Route>(currentRoute());
  const [user, setUser] = useState<User | null>(getStoredUser());
  const [checkingSession, setCheckingSession] = useState(Boolean(getStoredToken()));

  useEffect(() => {
    const handler = () => setRoute(currentRoute());
    window.addEventListener("popstate", handler);
    window.addEventListener("hashchange", handler);
    return () => {
      window.removeEventListener("popstate", handler);
      window.removeEventListener("hashchange", handler);
    };
  }, []);

  useEffect(() => {
    if (!getStoredToken()) {
      setCheckingSession(false);
      return;
    }

    api.me()
      .then((currentUser) => {
        storeUser(currentUser);
        setUser(currentUser);
        setCheckingSession(false);
      })
      .catch(() => {
        clearSession();
        setUser(null);
        setCheckingSession(false);
        navigate("/login");
      });
  }, []);

  useEffect(() => {
    if (!user) return;

    const allowedRoutes = getAllowedRoutes(user.role);
    if (!allowedRoutes.includes(route)) {
      navigate(defaultRouteForRole(user.role));
    }
  }, [route, user]);

  const logout = () => {
    clearSession();
    setUser(null);
    navigate("/login");
  };

  const navigation = getNavigation(user?.role);

  const shell = (content: ReactElement) => (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" onClick={() => navigate(user ? `/${defaultRouteForRole(user.role)}` : "/login")}>CredBridge</a>
        <nav>
          {navigation.map((item) => (
            <a key={item.route} className={route === item.route ? "active" : ""} onClick={() => navigate(`/${item.route}`)}>
              {item.label}
            </a>
          ))}
        </nav>
        <div className="profile">
          <strong>{user?.fullName || "Guest"}</strong>
          <span>{user?.role || "Not signed in"}</span>
          {user ? <button className="ghost" onClick={logout}>Sign out</button> : <button onClick={() => navigate("/login")}>Sign in</button>}
        </div>
      </aside>
      <main>{content}</main>
    </div>
  );

  if (checkingSession) {
    return <main className="auth-layout"><section className="auth-panel"><p>Checking session...</p></section></main>;
  }

  if (!user && route !== "register") {
    return <AuthPage mode="login" onAuthed={setUser} />;
  }

  if (route === "register") {
    return <AuthPage mode="register" onAuthed={setUser} />;
  }

  const pages: Record<Route, ReactElement> = {
    login: <AuthPage mode="login" onAuthed={setUser} />,
    register: <AuthPage mode="register" onAuthed={setUser} />,
    dashboard: <BorrowerDashboard />,
    application: <ApplicationForm />,
    report: <ReportPage />,
    documents: <DocumentUploadPage />,
    consent: <ConsentPage />,
    admin: <AdminDashboard user={user!} />
  };

  return shell(pages[route] || pages.dashboard);
}

function getAllowedRoutes(role: UserRole): Route[] {
  if (role === "ADMIN") return adminRoutes;
  if (role === "LENDER") return lenderRoutes;
  return borrowerRoutes;
}

function defaultRouteForRole(role: UserRole): Route {
  return role === "ADMIN" || role === "LENDER" ? "admin" : "dashboard";
}

function getNavigation(role?: UserRole) {
  if (role === "ADMIN") {
    return [{ route: "admin" as const, label: "Admin Dashboard" }];
  }

  if (role === "LENDER") {
    return [{ route: "admin" as const, label: "Review Queue" }];
  }

  return [
    { route: "dashboard" as const, label: "Dashboard" },
    { route: "application" as const, label: "Application" },
    { route: "report" as const, label: "Score Report" },
    { route: "documents" as const, label: "Documents" },
    { route: "consent" as const, label: "Consent" }
  ];
}

function AuthPage({ mode, onAuthed }: { mode: "login" | "register"; onAuthed: (user: User) => void }) {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [selectedRole, setSelectedRole] = useState<UserRole>("BORROWER");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setLoading(true);

    try {
      if (mode === "register") {
        await api.register({ fullName, email, password, role: selectedRole });
      }

      const auth = await api.login({ email, password });
      if (mode === "login" && auth.user.role !== selectedRole) {
        clearSession();
        throw new Error(`This account is registered as ${label(auth.user.role)}. Select ${label(auth.user.role)} mode to continue.`);
      }
      storeSession(auth);
      onAuthed(auth.user);
      navigate(`/${defaultRouteForRole(auth.user.role)}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="auth-layout">
      <section className="auth-panel">
        <div>
          <p className="eyebrow">CredBridge AI</p>
          <h1>{mode === "register" ? "Create account" : "Welcome back"}</h1>
        </div>
        <div className="segmented-control auth-role-control" role="group" aria-label={mode === "register" ? "Register role" : "Login role"}>
          {authRoles.map((role) => (
            <button type="button" key={role} className={selectedRole === role ? "active" : ""} onClick={() => setSelectedRole(role)}>
              {label(role)}
            </button>
          ))}
        </div>
        {mode === "login" && (
          <p className="muted">Choose the role for the account you are signing into.</p>
        )}
        {mode === "register" && (
          <p className="muted">Choose the role for the new account. This controls which dashboard opens after login.</p>
        )}
        <form onSubmit={submit} className="stack">
          {mode === "register" && (
            <label>Full name<input value={fullName} onChange={(e) => setFullName(e.target.value)} required /></label>
          )}
          <label>Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
          <label>Password<input type="password" minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
          {error && <p className="error">{error}</p>}
          <button disabled={loading}>{loading ? "Working..." : mode === "register" ? "Register" : "Login"}</button>
        </form>
        <button className="link-button" onClick={() => navigate(mode === "register" ? "/login" : "/register")}>
          {mode === "register" ? "Already have an account? Login" : "Need an account? Register"}
        </button>
      </section>
    </main>
  );
}

function BorrowerDashboard() {
  const [applications, setApplications] = useState<ApplicationSummary[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api.listApplications().then(setApplications).catch((err) => setError(err.message));
  }, []);

  const latest = applications[0];

  return (
    <Page title="Borrower dashboard" action={<button onClick={() => navigate("/application")}>New application</button>}>
      {error && <p className="error">{error}</p>}
      <div className="metric-grid">
        <Metric label="Applications" value={applications.length} />
        <Metric label="Latest status" value={latest?.status || "None"} />
        <Metric label="Requested amount" value={latest ? currency.format(Number(latest.requestedAmount)) : currency.format(0)} />
        <BackendStatus />
      </div>
      <section className="mode-grid">
        <ModeCard
          title="Basic Mode"
          value="Fast pre-check"
          details={["Self-declared income and expenses", "Rules-based score", "No document upload required"]}
          action={<button onClick={() => navigate("/application")}>Start basic check</button>}
        />
        <ModeCard
          title="Verified Document Mode"
          value="Evidence-backed scoring"
          details={["Upload salary, bank, tax, or business records", "OCR extraction plus mismatch checks", "Groq explains recommendation when available"]}
          action={<button className="ghost" onClick={() => selectApplicationMode("VERIFIED")}>Start verified review</button>}
        />
      </section>
      <ApplicationTable applications={applications} />
    </Page>
  );
}

function ApplicationForm() {
  const [mode, setMode] = useState<ApplicationMode>(initialApplicationMode);
  const [consentAccepted, setConsentAccepted] = useState(false);
  const [form, setForm] = useState<BasicApplicationRequest>({
    fullName: getStoredUser()?.fullName || "",
    employmentType: "SALARIED",
    monthlyIncome: 50000,
    monthlyExpenses: 20000,
    existingDebtPayment: 5000,
    repaymentHistory: "GOOD",
    incomeStability: "STABLE",
    requestedAmount: 200000,
    tenureMonths: 24
  });
  const [score, setScore] = useState<ScoreReport | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const disposableIncome = Math.max(0, form.monthlyIncome - form.monthlyExpenses - form.existingDebtPayment);
  const estimatedDti = form.monthlyIncome > 0 ? (form.existingDebtPayment / form.monthlyIncome) * 100 : 0;
  const estimatedInstallment = form.tenureMonths > 0 ? form.requestedAmount / form.tenureMonths : 0;
  const requiredDocuments = form.employmentType === "SALARIED" ? salariedDocuments : businessDocuments;

  const update = (key: keyof BasicApplicationRequest, value: string) => {
    const numeric = ["monthlyIncome", "monthlyExpenses", "existingDebtPayment", "requestedAmount", "tenureMonths"];
    setForm((prev) => ({ ...prev, [key]: numeric.includes(key) ? Number(value) : value }));
  };

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    if (!consentAccepted) {
      setError("Consent is required before CredBridge can process this credit assessment.");
      return;
    }
    setLoading(true);
    try {
      if (mode === "VERIFIED") {
        const application = await api.createVerifiedApplication(form as VerifiedApplicationRequest);
        sessionStorage.setItem("last_application_id", String(application.id));
        navigate("/documents");
        return;
      }

      const result = await api.createBasicApplication(form);
      setScore({
        ...result,
        fullName: form.fullName,
        mode: "BASIC",
        status: "SCORED",
        requestedAmount: form.requestedAmount,
        tenureMonths: form.tenureMonths,
        employmentType: form.employmentType,
        monthlyIncome: form.monthlyIncome,
        monthlyExpenses: form.monthlyExpenses,
        existingDebtPayment: form.existingDebtPayment,
        repaymentHistory: form.repaymentHistory,
        incomeStability: form.incomeStability
      });
      sessionStorage.setItem("last_application_id", String(result.applicationId));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not submit application");
    } finally {
      setLoading(false);
    }
  }

  return (
    <Page title="Application form">
      <div className="segmented-control" role="group" aria-label="Application mode">
        <button type="button" className={mode === "BASIC" ? "active" : ""} onClick={() => { setMode("BASIC"); setScore(null); }}>Basic Mode</button>
        <button type="button" className={mode === "VERIFIED" ? "active" : ""} onClick={() => { setMode("VERIFIED"); setScore(null); }}>Verified Mode</button>
      </div>
      <section className="summary-grid">
        <Metric label="Disposable income" value={currency.format(disposableIncome)} />
        <Metric label="Estimated DTI" value={`${estimatedDti.toFixed(2)}%`} />
        <Metric label="Approx. monthly instalment" value={currency.format(estimatedInstallment)} />
        <Metric label="Assessment mode" value={mode === "BASIC" ? "Self-declared" : "Document verified"} />
      </section>
      <form className="form-grid" onSubmit={submit}>
        <label>Full name<input value={form.fullName} onChange={(e) => update("fullName", e.target.value)} required /></label>
        <label>Employment type<Select value={form.employmentType} values={employmentTypes} onChange={(value) => update("employmentType", value)} /></label>
        <label>Monthly income<input type="number" min="1" value={form.monthlyIncome} onChange={(e) => update("monthlyIncome", e.target.value)} required /></label>
        <label>Monthly expenses<input type="number" min="0" value={form.monthlyExpenses} onChange={(e) => update("monthlyExpenses", e.target.value)} required /></label>
        <label>Existing debt payment<input type="number" min="0" value={form.existingDebtPayment} onChange={(e) => update("existingDebtPayment", e.target.value)} required /></label>
        <label>Repayment history<Select value={form.repaymentHistory} values={repaymentHistory} onChange={(value) => update("repaymentHistory", value as RepaymentHistory)} /></label>
        <label>Income stability<Select value={form.incomeStability} values={incomeStability} onChange={(value) => update("incomeStability", value as IncomeStability)} /></label>
        <label>Requested amount<input type="number" min="1" value={form.requestedAmount} onChange={(e) => update("requestedAmount", e.target.value)} required /></label>
        <label>Tenure months<input type="number" min="1" value={form.tenureMonths} onChange={(e) => update("tenureMonths", e.target.value)} required /></label>
        <label className="consent-check">
          <input type="checkbox" checked={consentAccepted} onChange={(e) => setConsentAccepted(e.target.checked)} />
          I consent to CredBridge processing this application for credit assessment and fraud/risk checks.
        </label>
        <div className="form-actions">
          {error && <p className="error">{error}</p>}
          <button disabled={loading}>{loading ? "Submitting..." : mode === "VERIFIED" ? "Continue to documents" : "Submit application"}</button>
        </div>
      </form>
      {mode === "VERIFIED" && <DocumentChecklist title="Required verified documents" required={requiredDocuments} documents={[]} />}
      {score && (
        <>
          <ScoreCard score={score} />
          <section className="summary-card">
            <h2>{score.mode === "VERIFIED" ? "Verified assessment pending" : "Next step"}</h2>
            <p className="muted">
              {score.mode === "VERIFIED"
                ? "Your Verified Mode application is created. Upload documents to generate the evidence-backed score."
                : "This is a Basic Mode pre-qualification result. Add verified documents to convert this application into Verified Mode for evidence-backed scoring."}
            </p>
            <div className="table-actions">
              <button type="button" onClick={() => selectApplication(score.applicationId, "documents")}>
                {score.mode === "VERIFIED" ? "Upload documents" : "Convert to verified mode"}
              </button>
              <button type="button" className="ghost" onClick={() => selectApplication(score.applicationId, "report")}>Open full report</button>
            </div>
          </section>
        </>
      )}
    </Page>
  );
}

function ReportPage() {
  const [applicationId, setApplicationId] = useState(sessionStorage.getItem("last_application_id") || "");
  const [report, setReport] = useState<ScoreReport | null>(null);
  const [error, setError] = useState("");
  const [downloading, setDownloading] = useState(false);

  async function load(event?: FormEvent) {
    event?.preventDefault();
    setError("");
    setReport(null);
    try {
      sessionStorage.setItem("last_application_id", applicationId);
      const result = await api.getReport(Number(applicationId));
      setReport(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Report not found");
    }
  }

  useEffect(() => {
    if (applicationId) {
      load();
    }
  }, []);

  async function downloadPdf() {
    setError("");
    setDownloading(true);
    try {
      sessionStorage.setItem("last_application_id", applicationId);
      const pdf = await api.downloadReportPdf(Number(applicationId));
      const url = URL.createObjectURL(pdf);
      const link = document.createElement("a");
      link.href = url;
      link.download = `credbridge-report-${applicationId}.pdf`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not download PDF");
    } finally {
      setDownloading(false);
    }
  }

  return (
    <Page title="Score/report page">
      <form className="inline-form" onSubmit={load}>
        <label>Application ID<input value={applicationId} onChange={(e) => setApplicationId(e.target.value)} required /></label>
        <button>Load report</button>
        <button type="button" className="ghost" onClick={downloadPdf} disabled={!applicationId || downloading}>
          {downloading ? "Downloading..." : "Download PDF"}
        </button>
      </form>
      {error && <p className="error">{error}</p>}
      {report && <ScoreCard score={report} />}
    </Page>
  );
}

function DocumentUploadPage() {
  const [applicationId, setApplicationId] = useState(sessionStorage.getItem("last_application_id") || "");
  const [applications, setApplications] = useState<ApplicationSummary[]>([]);
  const [documentType, setDocumentType] = useState<DocumentType>("BANK_STATEMENT");
  const [file, setFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [documents, setDocuments] = useState<UploadedDocument[]>([]);
  const [report, setReport] = useState<ScoreReport | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [submittingScore, setSubmittingScore] = useState(false);
  const [documentProfile, setDocumentProfile] = useState<"SALARIED" | "BUSINESS">("SALARIED");
  const hasPendingDocuments = documents.some((doc) => doc.status === "UPLOADED" || doc.status === "PROCESSING");
  const selectedApplication = applications.find((app) => String(app.id) === applicationId);
  const requiredDocuments = documentProfile === "SALARIED" ? salariedDocuments : businessDocuments;
  const completedDocuments = requiredDocuments.filter((type) => documents.some((doc) => doc.documentType === type && doc.status === "PROCESSED"));
  const readyForReview = coreVerifiedDocuments.every((type) => documents.some((doc) => doc.documentType === type && doc.status === "PROCESSED"));

  useEffect(() => {
    api.listApplications()
      .then((items) => {
        setApplications(items);
        if (!applicationId && items[0]) {
          setApplicationId(String(items[0].id));
          sessionStorage.setItem("last_application_id", String(items[0].id));
        }
      })
      .catch((err) => setError(err.message));
  }, []);

  useEffect(() => {
    if (!applicationId) return;
    sessionStorage.setItem("last_application_id", applicationId);
    setReport(null);
    refresh(Number(applicationId)).catch((err) => setError(err.message));
  }, [applicationId]);

  useEffect(() => {
    if (!applicationId || !hasPendingDocuments) return;

    const timer = window.setInterval(() => {
      refresh(Number(applicationId)).catch((err) => setError(err.message));
    }, 4000);

    return () => window.clearInterval(timer);
  }, [applicationId, hasPendingDocuments]);

  async function refresh(id = Number(applicationId)) {
    if (!id) return;
    setDocuments(await api.listDocuments(id));
  }

  function resetFileSelection() {
    setFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  }

  function selectFile(nextFile?: File) {
    setSuccess("");
    if (!nextFile) {
      resetFileSelection();
      return;
    }

    if (!allowedDocumentTypes.includes(nextFile.type)) {
      resetFileSelection();
      setError("Upload blocked. Documents must be PDF, PNG, or JPEG files and pass backend security checks.");
      return;
    }

    setError("");
    setFile(nextFile);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file) return;
    setError("");
    setSuccess("");
    try {
      const uploaded = await api.uploadDocument(Number(applicationId), documentType, file);
      setSuccess(`Document #${uploaded.id} uploaded for application #${uploaded.applicationId}.`);
      resetFileSelection();
      await refresh();
    } catch (err) {
      setError(formatUploadError(err));
    }
  }

  async function submitForScore() {
    setError("");
    setSuccess("");
    setReport(null);

    if (!applicationId) {
      setError("Select an application before submitting for verified score.");
      return;
    }

    if (hasPendingDocuments) {
      setError("Wait until uploaded documents finish processing before submitting for verified score.");
      return;
    }

    const processedDocuments = documents.filter((doc) => doc.status === "PROCESSED");
    if (processedDocuments.length === 0) {
      setError("Upload at least one processed document before submitting for verified score.");
      return;
    }

    setSubmittingScore(true);
    try {
      const result = await api.getReport(Number(applicationId));
      setReport(result);
      setSuccess("Verified score generated from the processed documents.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not generate verified score");
    } finally {
      setSubmittingScore(false);
    }
  }

  async function deleteDocument(documentId: number) {
    setError("");
    setSuccess("");
    try {
      await api.deleteDocument(documentId);
      setSuccess(`Document #${documentId} deleted.`);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete document");
    }
  }

  return (
    <Page title="Document upload page">
      <section className="summary-grid">
        <Metric label="Selected application" value={selectedApplication ? `#${selectedApplication.id}` : "-"} />
        <Metric label="Mode" value={selectedApplication ? label(selectedApplication.mode) : "-"} />
        <Metric label="Processed required docs" value={`${completedDocuments.length}/${requiredDocuments.length}`} />
        <Metric label="Review readiness" value={readyForReview ? "Core complete" : "Documents needed"} />
      </section>
      <form className="form-grid compact" onSubmit={submit}>
        <label>Application<select value={applicationId} onChange={(e) => setApplicationId(e.target.value)} required>
          <option value="" disabled>Select application</option>
          {applications.map((app) => (
            <option key={app.id} value={app.id}>
              #{app.id} - {app.fullName} - {currency.format(Number(app.requestedAmount))}
            </option>
          ))}
        </select></label>
        <label>Client profile<select value={documentProfile} onChange={(e) => setDocumentProfile(e.target.value as "SALARIED" | "BUSINESS")}>
          <option value="SALARIED">Salaried client</option>
          <option value="BUSINESS">Business client</option>
        </select></label>
        <label>Document type<Select value={documentType} values={documentTypes} onChange={(value) => setDocumentType(value as DocumentType)} /></label>
        <label>File<input ref={fileInputRef} type="file" accept=".pdf,image/png,image/jpeg" onClick={(e) => {
          e.currentTarget.value = "";
        }} onChange={(e) => {
          const nextFile = e.target.files?.[0];
          selectFile(nextFile);
          if (nextFile && !allowedDocumentTypes.includes(nextFile.type)) e.currentTarget.value = "";
        }} required /></label>
        <div className="form-actions">
          {error && <p className="error">{error}</p>}
          {success && <p className="success">{success}</p>}
          <button>Upload document</button>
          <button type="button" className="ghost" onClick={() => refresh()}>Refresh list</button>
          {hasPendingDocuments && <p className="muted">Checking document processing status...</p>}
        </div>
      </form>
      <DocumentChecklist title="Verification checklist" required={requiredDocuments} documents={documents} />
      <section className="summary-card">
        <h2>Submit verification</h2>
        <p className="muted">Upload each required document from the dropdown. When the documents are processed, submit them to view the verified score interface.</p>
        <div className="table-actions">
          <button type="button" onClick={submitForScore} disabled={submittingScore || hasPendingDocuments || documents.every((doc) => doc.status !== "PROCESSED")}>
            {submittingScore ? "Generating score..." : "Submit for verified score"}
          </button>
          {hasPendingDocuments && <span className="muted">Document processing is still in progress.</span>}
        </div>
      </section>
      {report && (
        <>
          <ScoreCard score={report} />
          <section className="summary-card">
            <h2>Verified score ready</h2>
            <p className="muted">The uploaded document has been processed and the application now has an evidence-backed verified assessment.</p>
            <div className="table-actions">
              <button type="button" onClick={() => selectApplication(report.applicationId, "report")}>Open full report</button>
            </div>
          </section>
        </>
      )}
      <DataTable columns={["ID", "Type", "File", "Status", "Created", "Actions"]} rows={documents.map((doc) => [
        doc.id,
        label(doc.documentType),
        doc.originalFilename,
        <DocumentStatusBadge key={`${doc.id}-status`} status={doc.status} />,
        formatDate(doc.createdAt),
        <button key={`${doc.id}-delete`} type="button" className="ghost danger" onClick={() => deleteDocument(doc.id)}>Delete</button>
      ])} />
    </Page>
  );
}

function DocumentChecklist({ title, required, documents }: { title: string; required: DocumentType[]; documents: UploadedDocument[] }) {
  const uploadedTypes = new Set(documents.map((doc) => doc.documentType));
  const processedTypes = new Set(documents.filter((doc) => doc.status === "PROCESSED").map((doc) => doc.documentType));

  return (
    <section className="summary-card">
      <h2>{title}</h2>
      <div className="checklist-grid">
        {required.map((type) => {
          const status = processedTypes.has(type) ? "Processed" : uploadedTypes.has(type) ? "Uploaded" : "Needed";
          const tone = processedTypes.has(type) ? "processed" : uploadedTypes.has(type) ? "uploaded" : "submitted";
          return (
            <div className="checklist-item" key={type}>
              <strong>{label(type)}</strong>
              <span className={`badge ${tone}`}>{status}</span>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function AdminDashboard({ user }: { user: User }) {
  if (user.role === "LENDER") {
    return <LenderReviewQueue />;
  }

  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [applications, setApplications] = useState<AdminApplication[]>([]);
  const [auditEvents, setAuditEvents] = useState<PrivacyAuditEvent[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function loadAdminData() {
    setError("");
    setLoading(true);
    Promise.all([api.adminOverview(), api.adminApplications(), api.auditEvents().catch(() => [])])
      .then(([overviewData, applicationData, auditData]) => {
        setOverview(overviewData);
        setApplications(applicationData);
        setAuditEvents(auditData);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    loadAdminData();
  }, []);

  return (
    <Page title="Admin dashboard" action={<button className="ghost" onClick={loadAdminData} disabled={loading}>{loading ? "Refreshing..." : "Refresh"}</button>}>
      {error && <p className="error">{error}</p>}
      <div className="metric-grid">
        <Metric label="Applications" value={overview?.totalApplications ?? 0} />
        <Metric label="Users" value={overview?.totalUsers ?? 0} />
        <Metric label="Average score" value={overview?.averageScore ?? 0} />
        <Metric label="Requested amount" value={currency.format(Number(overview?.totalRequestedAmount ?? 0))} />
      </div>
      <section className="summary-grid">
        <SummaryGroup title="Application status" items={statusSummary.map((status) => ({
          label: label(status),
          value: overview?.applicationsByStatus?.[status] ?? 0,
          tone: status.toLowerCase()
        }))} />
        <SummaryGroup title="Risk levels" items={riskSummary.map((risk) => ({
          label: label(risk),
          value: overview?.scoresByRiskLevel?.[risk] ?? 0,
          tone: risk.toLowerCase()
        }))} />
      </section>
      <DataTable columns={[
        "ID",
        "Mode",
        "Borrower",
        "Email",
        "Status",
        "Amount",
        "Score",
        "Risk",
        "AI Status",
        "AI Confidence",
        "Cash Flow",
        "Business Health"
      ]} rows={applications.map((app) => [
        app.id,
        label(app.mode),
        app.fullName,
        app.userEmail,
        <StatusBadge key={`${app.id}-status`} status={app.status} />,
        currency.format(Number(app.requestedAmount)),
        app.score ?? "-",
        app.riskLevel ? <RiskBadge key={`${app.id}-risk`} risk={app.riskLevel} /> : "-",
        formatAiStatus(app),
        formatOptionalPercent(app.modelConfidenceScore),
        formatOptionalNumber(app.cashFlowStabilityScore),
        formatOptionalNumber(app.businessHealthScore)
      ])} />
      <section className="table-section">
        <h2>Privacy audit events</h2>
        <DataTable columns={["ID", "User", "Application", "Event", "Details", "Created"]} rows={auditEvents.map((event) => [
          event.id,
          event.userEmail || event.userId || "-",
          event.applicationId || "-",
          event.eventType || event.action || "-",
          event.details || "-",
          formatDate(event.createdAt)
        ])} />
      </section>
    </Page>
  );
}

function LenderReviewQueue() {
  const [applications, setApplications] = useState<ApplicationSummary[]>([]);
  const [reviewNotes, setReviewNotes] = useState<Record<number, string>>({});
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  async function loadApplications() {
    setError("");
    setLoading(true);
    api.listApplications()
      .then(setApplications)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    loadApplications();
  }, []);

  async function updateStatus(applicationId: number, status: ApplicationStatus) {
    setError("");
    setSuccess("");
    try {
      await api.updateApplicationReview(applicationId, status, reviewNotes[applicationId] || "");
      setSuccess(`Application #${applicationId} moved to ${label(status)}.`);
      setReviewNotes((prev) => ({ ...prev, [applicationId]: "" }));
      await loadApplications();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update application status");
    }
  }

  return (
    <Page title="Review queue" action={<button className="ghost" onClick={loadApplications} disabled={loading}>{loading ? "Refreshing..." : "Refresh"}</button>}>
      {error && <p className="error">{error}</p>}
      {success && <p className="success">{success}</p>}
      <div className="metric-grid">
        <Metric label="Applications" value={applications.length} />
        <Metric label="Scored" value={applications.filter((app) => app.status === "SCORED").length} />
        <Metric label="Under review" value={applications.filter((app) => app.status === "UNDER_REVIEW").length} />
        <BackendStatus />
      </div>
      <DataTable columns={["ID", "Mode", "Borrower", "Status", "Amount", "Tenure", "Review notes", "Decision"]} rows={applications.map((app) => [
        app.id,
        label(app.mode),
        app.fullName,
        <StatusBadge key={`${app.id}-status`} status={app.status} />,
        currency.format(Number(app.requestedAmount)),
        `${app.tenureMonths} months`,
        <input
          key={`${app.id}-notes`}
          className="table-input"
          value={reviewNotes[app.id] ?? app.reviewNotes ?? ""}
          onChange={(event) => setReviewNotes((prev) => ({ ...prev, [app.id]: event.target.value }))}
          placeholder="Decision note"
        />,
        <div className="table-actions" key={`${app.id}-decision`}>
          {reviewStatuses.map((status) => (
            <button key={status} className="ghost" type="button" onClick={() => updateStatus(app.id, status)}>{label(status)}</button>
          ))}
          <button className="ghost" type="button" onClick={() => selectApplication(app.id, "report")}>Report</button>
        </div>
      ])} />
    </Page>
  );
}

function ConsentPage() {
  const [consents, setConsents] = useState<PrivacyConsent[]>([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  async function loadConsents() {
    setError("");
    setLoading(true);
    api.listConsents()
      .then(setConsents)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    loadConsents();
  }, []);

  async function revoke(applicationId: number) {
    setError("");
    setSuccess("");
    try {
      await api.revokeConsent(applicationId);
      setSuccess(`Consent revoked for application #${applicationId}.`);
      await loadConsents();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not revoke consent");
    }
  }

  return (
    <Page title="Consent" action={<button className="ghost" onClick={loadConsents} disabled={loading}>{loading ? "Refreshing..." : "Refresh"}</button>}>
      {error && <p className="error">{error}</p>}
      {success && <p className="success">{success}</p>}
      <DataTable columns={["ID", "Application", "Type", "Status", "Granted", "Revoked", "Actions"]} rows={consents.map((consent) => [
        consent.id,
        consent.applicationId,
        label(consent.purpose || consent.consentType || "CREDIT_ASSESSMENT"),
        consent.revoked || consent.granted === false || consent.accepted === false ? "Revoked" : "Active",
        formatDate(consent.grantedAt || consent.createdAt),
        formatDate(consent.revokedAt || undefined),
        consent.revoked || consent.granted === false || consent.accepted === false
          ? "-"
          : <button key={`${consent.id}-revoke`} type="button" className="ghost danger" onClick={() => revoke(consent.applicationId)}>Revoke</button>
      ])} />
    </Page>
  );
}

function Page({ title, action, children }: { title: string; action?: ReactElement; children: ReactNode }) {
  return (
    <section className="page">
      <header className="page-header">
        <h1>{title}</h1>
        {action}
      </header>
      {children}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: ReactNode }) {
  return <div className="metric"><span>{label}</span><strong>{value}</strong></div>;
}

function BackendStatus() {
  const [status, setStatus] = useState("Checking");

  useEffect(() => {
    api.health()
      .then((health) => setStatus(health.status === "running" ? "Online" : label(health.status)))
      .catch(() => setStatus("Offline"));
  }, []);

  return <Metric label="Backend" value={status} />;
}

function ModeCard({ title, value, details, action }: { title: string; value: string; details: string[]; action: ReactElement }) {
  return (
    <div className="summary-card">
      <span className="eyebrow">{title}</span>
      <strong>{value}</strong>
      <ul className="compact-list">{details.map((detail) => <li key={detail}>{detail}</li>)}</ul>
      {action}
    </div>
  );
}

function ApplicationTable({ applications }: { applications: ApplicationSummary[] }) {
  return <DataTable columns={["ID", "Mode", "Name", "Status", "Amount", "Tenure", "Created", "Actions"]} rows={applications.map((app) => [
    app.id,
    label(app.mode),
    app.fullName,
    <StatusBadge key={`${app.id}-status`} status={app.status} />,
    currency.format(Number(app.requestedAmount)),
    `${app.tenureMonths} months`,
    formatDate(app.createdAt),
    <div className="table-actions" key={`${app.id}-actions`}>
      <button className="ghost" onClick={() => selectApplication(app.id, "report")}>View Report</button>
      <button className="ghost" onClick={() => selectApplication(app.id, "documents")}>Upload Documents</button>
    </div>
  ])} />;
}

function selectApplication(applicationId: number, route: Route) {
  sessionStorage.setItem("last_application_id", String(applicationId));
  navigate(`/${route}`);
}

function selectApplicationMode(mode: ApplicationMode) {
  sessionStorage.setItem(APPLICATION_MODE_KEY, mode);
  navigate("/application");
}

function initialApplicationMode(): ApplicationMode {
  const storedMode = sessionStorage.getItem(APPLICATION_MODE_KEY);
  sessionStorage.removeItem(APPLICATION_MODE_KEY);
  return storedMode === "VERIFIED" ? "VERIFIED" : "BASIC";
}

function DataTable({ columns, rows }: { columns: string[]; rows: Array<Array<ReactNode>> }) {
  return (
    <div className="table-wrap">
      <table>
        <thead><tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr></thead>
        <tbody>
          {rows.length === 0 ? <tr><td colSpan={columns.length}>No records yet.</td></tr> : rows.map((row, index) => (
            <tr key={index}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ScoreCard({ score }: { score: ScoreReport }) {
  const riskClass = `risk ${String(score.riskLevel || "submitted").toLowerCase()}`;
  const isBasicMode = score.mode === "BASIC" || !score.mode;
  const isVerifiedPending = score.mode === "VERIFIED" && (score.score === null || score.score === undefined);
  return (
    <section className="score-layout">
      <div className="application-id-banner">
        <div>
          <span>Application ID</span>
          <strong>#{score.applicationId}</strong>
        </div>
        {isBasicMode && (
          <p className="conversion-note">Basic Mode uses self-declared data. Upload documents to convert this application to Verified Mode.</p>
        )}
        {isVerifiedPending && (
          <p className="conversion-note">Verified Mode is waiting for documents. Upload files to calculate the verified score.</p>
        )}
        <div>
          <button className="ghost" onClick={() => selectApplication(score.applicationId, "report")}>Open report</button>
          <button className="ghost" onClick={() => selectApplication(score.applicationId, "documents")}>
            {isBasicMode ? "Convert to verified" : "Upload documents"}
          </button>
        </div>
      </div>
      <div className="score-ring">
        <span>{isVerifiedPending ? "Verified score" : "Score"}</span>
        <strong className={isVerifiedPending ? "pending-score" : ""}>{isVerifiedPending ? "Pending" : score.score ?? "-"}</strong>
        <em className={riskClass}>{score.riskLevel ? label(score.riskLevel) : label(score.status || "SUBMITTED")}</em>
      </div>
      <div className="score-details">
        <Metric label="Borrower" value={score.fullName || "-"} />
        <Metric label="Mode" value={score.mode ? label(score.mode) : "-"} />
        <Metric label="Status" value={score.status ? label(score.status) : "-"} />
        <Metric label="Requested amount" value={formatOptionalCurrency(score.requestedAmount)} />
        <Metric label="Debt to income" value={formatOptionalPercent(score.debtToIncomeRatio)} />
        <Metric label="Expense ratio" value={formatOptionalPercent(score.expenseRatio)} />
        <Metric label="Repayment capacity" value={formatOptionalCurrency(score.repaymentCapacity)} />
        <Metric label="Suggested limit" value={formatOptionalCurrency(score.suggestedLoanLimit)} />
        <Metric label="Model confidence" value={formatOptionalPercent(score.modelConfidenceScore)} />
        <Metric label="Default risk" value={formatDefaultRisk(score.defaultRisk)} />
        <Metric label="Cash flow stability" value={formatOptionalNumber(score.cashFlowStabilityScore)} />
        <Metric label="Business health" value={formatOptionalNumber(score.businessHealthScore)} />
        <Metric label="Verified documents" value={score.verifiedDocumentCount ?? "-"} />
        <Metric label="LLM model" value={score.llmModel || "-"} />
      </div>
      {(score.riskExplanation || score.lendingRecommendation || score.llmReasoningSummary) && (
        <div className="verified-insights">
          {score.riskExplanation && <Metric label="Risk explanation" value={score.riskExplanation} />}
          {score.lendingRecommendation && <Metric label="Lending recommendation" value={score.lendingRecommendation} />}
          {score.llmReasoningSummary && <Metric label="AI reasoning summary" value={score.llmReasoningSummary} />}
          {score.llmPromptVersion && <Metric label="Prompt version" value={score.llmPromptVersion} />}
          {score.reviewNotes && <Metric label="Review notes" value={score.reviewNotes} />}
        </div>
      )}
      <div className="factor-grid">
        <FactorList title="Positive factors" values={score.positiveFactors || []} />
        <FactorList title="Risk factors" values={score.riskFactors || []} />
        <FactorList title="Fraud indicators" values={score.fraudIndicators || []} />
      </div>
    </section>
  );
}

function FactorList({ title, values }: { title: string; values: string[] }) {
  return (
    <div className="factors">
      <h2>{title}</h2>
      <ul>{values.map((value) => <li key={value}>{value}</li>)}</ul>
    </div>
  );
}

function SummaryGroup({ title, items }: { title: string; items: Array<{ label: string; value: number; tone: string }> }) {
  return (
    <div className="summary-card">
      <h2>{title}</h2>
      <div className="summary-list">
        {items.map((item) => (
          <div className="summary-row" key={item.label}>
            <span className={`badge ${item.tone}`}>{item.label}</span>
            <strong>{item.value}</strong>
          </div>
        ))}
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: ApplicationStatus }) {
  return <span className={`badge ${status.toLowerCase()}`}>{label(status)}</span>;
}

function RiskBadge({ risk }: { risk: RiskLevel }) {
  return <span className={`badge ${risk.toLowerCase()}`}>{label(risk)}</span>;
}

function DocumentStatusBadge({ status }: { status: UploadedDocument["status"] }) {
  return <span className={`badge ${status.toLowerCase()}`}>{label(status)}</span>;
}

function Select<T extends string>({ value, values, onChange }: { value: T; values: readonly T[]; onChange: (value: T) => void }) {
  return (
    <select value={value} onChange={(event) => onChange(event.target.value as T)}>
      {values.map((item) => <option key={item} value={item}>{label(item)}</option>)}
    </select>
  );
}

function label(value: string) {
  return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (match) => match.toUpperCase());
}

function formatOptionalNumber(value?: number | null) {
  return value === null || value === undefined ? "-" : Number(value).toFixed(2);
}

function formatOptionalPercent(value?: number | null) {
  return value === null || value === undefined ? "-" : `${Number(value).toFixed(2)}%`;
}

function formatOptionalCurrency(value?: number | null) {
  return value === null || value === undefined ? "-" : currency.format(Number(value));
}

function formatAiStatus(app: AdminApplication) {
  if (app.lendingRecommendation) return label(app.lendingRecommendation);
  if (app.mode !== "VERIFIED") return "Basic mode";
  return app.status === "SCORED" ? "AI output unavailable" : `Pending AI score (${label(app.status)})`;
}

function formatDefaultRisk(value?: string | number | boolean | null) {
  if (value === null || value === undefined) return "-";
  return typeof value === "string" ? label(value) : String(value);
}

function formatUploadError(err: unknown) {
  const message = err instanceof Error ? err.message : "";
  if (/unable to extract text|ocr|text extraction/i.test(message)) {
    return `Upload failed because text could not be extracted from the document: ${message}`;
  }

  if (/mime|content type|file type|pdf|png|jpeg|jpg|security|malware|virus|unsafe/i.test(message)) {
    return `Upload blocked by file validation/security checks: ${message}`;
  }

  return message || "Upload failed. Documents must be PDF, PNG, or JPEG files.";
}

function formatDate(value?: string) {
  return value ? new Date(value).toLocaleString() : "-";
}
