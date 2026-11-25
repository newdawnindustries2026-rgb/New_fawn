const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(msAgo = 2000) {
  return new Date(Date.now() - msAgo);
}

test("Unauthenticated users cannot read or write any user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("personas").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Anon",
      activePersonaId: "preset_elena",
      activeModelId: "gemini-3.8-flash",
      voiceAutoPlay: true,
      searchGrounding: false,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("UserStudioSettings: owner create, partial update, cross-user denial, and ghost-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Candidate",
      activePersonaId: "preset_elena",
      activeModelId: "gemini-3.8-flash",
      voiceAutoPlay: true,
      searchGrounding: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update allowed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activeModelId: "gemini-4-argon",
      searchGrounding: false,
      updatedAt: pastDate(1000),
    })
  );

  // Immutable createdAt mutation rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Shadow/ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Cross-user read & write denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: pastDate(500),
    })
  );
});

test("InterviewerPersona: owner create, update, list, delete; reject invalid bounds & extra id field", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  const personaRef = aliceDb.collection("users").doc(ALICE_UID).collection("personas").doc("custom_ava");

  await assertSucceeds(
    personaRef.set({
      userId: ALICE_UID,
      name: "Ava Sterling",
      roleTitle: "Staff ML Engineer",
      companyArchetype: "Frontier AI Lab",
      styleTag: "Socratic",
      warmth: 72,
      rigor: 88,
      followUpDepth: 90,
      cadence: "conversational",
      voicePitch: 1.05,
      voiceRate: 1.0,
      systemPrompt: "Conduct a realistic staff-level machine learning interview with natural spoken pacing.",
      presetDrawableKey: "elena",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner list subcollection succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("personas").get()
  );

  // Owner partial update succeeds
  await assertSucceeds(
    personaRef.update({
      warmth: 80,
      localPortraitFile: "avatar_custom_ava.jpg",
      updatedAt: pastDate(1000),
    })
  );

  // Reject out-of-range warmth & @DocumentId 'id' field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("personas").doc("bad_persona").set({
      id: "bad_persona",
      userId: ALICE_UID,
      name: "Bad",
      roleTitle: "Role",
      companyArchetype: "Corp",
      styleTag: "Tag",
      warmth: 120,
      rigor: 50,
      followUpDepth: 50,
      cadence: "conversational",
      voicePitch: 1.0,
      voiceRate: 1.0,
      systemPrompt: "Prompt",
      presetDrawableKey: "elena",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Cross-user list denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("personas").get()
  );

  await assertSucceeds(personaRef.delete());
});

test("ModelPreset: owner CRUD succeeds; future timestamp & invalid providerType rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(5000);
  const presetRef = aliceDb.collection("users").doc(ALICE_UID).collection("model_presets").doc("preset_openrouter");

  await assertSucceeds(
    presetRef.set({
      userId: ALICE_UID,
      label: "Claude 3.7 via OpenRouter",
      providerType: "openai_compatible",
      modelId: "anthropic/claude-3.7-sonnet",
      baseUrl: "https://openrouter.ai/api/v1/chat/completions",
      temperature: 0.7,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("model_presets").get()
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("model_presets").doc("future_ts").set({
      userId: ALICE_UID,
      label: "Future",
      providerType: "gemini",
      modelId: "gemini-3.8-flash",
      baseUrl: "",
      temperature: 0.7,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject invalid providerType
  await assertFails(
    presetRef.update({
      providerType: "unknown_provider",
      updatedAt: pastDate(1000),
    })
  );
});

test("InterviewSession & Messages: lifecycle transitions, terminal completion lock, and parent existence check", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);
  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_01");

  // Create active session
  await assertSucceeds(
    sessionRef.set({
      userId: ALICE_UID,
      personaId: "preset_elena",
      personaName: "Elena Vance",
      personaRole: "VP of Executive Strategy",
      targetRole: "Staff Product Manager",
      modelLabel: "Gemini 3.8 Flash",
      modelId: "gemini-3.8-flash",
      status: "active",
      turnCount: 0,
      overallScore: 0,
      clarityScore: 0,
      depthScore: 0,
      confidenceScore: 0,
      summaryFeedback: "",
      strengths: [],
      improvements: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Add message to existing session
  const msgRef = sessionRef.collection("messages").doc("msg_01");
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      senderRole: "interviewer",
      content: "Welcome in! Tell me about a product bet that didn't go as planned.",
      modelUsed: "gemini-3.8-flash",
      hasAttachment: false,
      attachmentLabel: "",
      sequenceIndex: 0,
      createdAt: pastDate(4000),
    })
  );

  // Messages are immutable (cannot update)
  await assertFails(msgRef.update({ content: "Tampered" }));

  // Message under non-existent session fails Parent Gate exists()
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("missing_sess")
      .collection("messages").doc("orphan_msg").set({
        userId: ALICE_UID,
        senderRole: "candidate",
        content: "Orphan",
        modelUsed: "",
        hasAttachment: false,
        attachmentLabel: "",
        sequenceIndex: 0,
        createdAt: pastDate(3000),
      })
  );

  // Action 1: Update active session turnCount and model mid-interview
  await assertSucceeds(
    sessionRef.update({
      turnCount: 2,
      modelLabel: "Gemini 4 Argon",
      modelId: "gemini-4-argon",
      updatedAt: pastDate(2000),
    })
  );

  // Action 2: Complete session with executive scorecard
  await assertSucceeds(
    sessionRef.update({
      status: "completed",
      turnCount: 6,
      overallScore: 88,
      clarityScore: 91,
      depthScore: 85,
      confidenceScore: 87,
      summaryFeedback: "Strong executive narrative with clear trade-off framing.",
      strengths: ["Structured framing", "Measured poise"],
      improvements: ["Quantify retention impact sooner"],
      updatedAt: pastDate(1000),
    })
  );

  // Terminal state lock: updating a completed session must fail
  await assertFails(
    sessionRef.update({
      overallScore: 100,
      updatedAt: pastDate(500),
    })
  );

  // List sessions & messages succeeds for owner, fails for Bob
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertSucceeds(sessionRef.collection("messages").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_01").collection("messages").get());
});
