const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

test("Unauthenticated user cannot read or write private LEON collections or overwrite creator stamp", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("routines").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(unauthDb.collection("app_branding").get());
  await assertFails(
    unauthDb.collection("app_branding").doc("creator_stamp").set({
      ownerId: ALICE_UID,
      creatorName: "Spoof",
      tagline: "",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set({
      userId: ALICE_UID,
      title: "Hack",
      notes: "",
      dueLabel: "",
      priority: "high",
      completed: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Public non-PII creator_stamp can be read on download but updated only by its original owner", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 10000);
  const now = new Date(Date.now() - 1000);

  // Arbitrary doc IDs in app_branding must fail
  await assertFails(
    aliceDb.collection("app_branding").doc("other_doc").set({
      ownerId: ALICE_UID,
      creatorName: "Alice",
      tagline: "Built by Alice",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Creator Alice publishes canonical creator_stamp
  await assertSucceeds(
    aliceDb.collection("app_branding").doc("creator_stamp").set({
      ownerId: ALICE_UID,
      creatorName: "Alice Boss",
      tagline: "Personalized LEON AI",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Any fresh downloader (even unauthenticated) can get creator_stamp (non-list)
  await assertSucceeds(unauthDb.collection("app_branding").doc("creator_stamp").get());
  await assertSucceeds(bobDb.collection("app_branding").doc("creator_stamp").get());

  // Bob cannot overwrite or hijack Alice's creator_stamp
  await assertFails(
    bobDb.collection("app_branding").doc("creator_stamp").update({
      creatorName: "Hijacked",
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("app_branding").doc("creator_stamp").delete());

  // Alice can update her creator_stamp name
  await assertSucceeds(
    aliceDb.collection("app_branding").doc("creator_stamp").update({
      creatorName: "Alice Chief",
      tagline: "Built for speed",
      updatedAt: now,
    })
  );
});

test("Owner can create, update, and list permanent memories, preferences with ownerName, routines, tasks, and conversations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 10000);
  const now = new Date(Date.now() - 1000);

  // Preferences with ownerName & creatorTagline
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      assistantName: "LEON AI",
      ownerName: "Alice Boss",
      creatorTagline: "Always Ready",
      defaultLanguage: "hi",
      alwaysReadyEnabled: true,
      memoryConsentGranted: true,
      voiceSpeed: 1.0,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      ownerName: "Alice Prime",
      defaultLanguage: "hinglish",
      updatedAt: now,
    })
  );

  // Permanent memory item
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      memoryKey: "Preferred Tea",
      memoryValue: "Ginger Masala Chai at 5 PM",
      category: "preference",
      approved: true,
      isPermanent: true,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").update({
      memoryValue: "Adrak Chai at 5 PM",
      updatedAt: now,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("memories").get());

  // Routine item
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("routines").doc("routine_1").set({
      userId: ALICE_UID,
      title: "Morning Briefing",
      triggerPhrase: "Good morning Leon",
      steps: ["Read notifications", "Speak weather", "List tasks"],
      enabled: true,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("routines").doc("routine_1").update({
      enabled: false,
      updatedAt: now,
    })
  );

  // Daily Task item
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Call Mom",
      notes: "Ask about dinner",
      dueLabel: "Today 7 PM",
      priority: "high",
      completed: false,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      completed: true,
      updatedAt: now,
    })
  );

  // Conversation item
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("msg_1").set({
      userId: ALICE_UID,
      role: "user",
      text: "Hey Leon",
      language: "hi",
      actionSummary: "",
      createdAt: past,
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's private data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const past = new Date(Date.now() - 10000);
    await context.firestore().collection("users").doc(ALICE_UID).collection("memories").doc("alice_mem").set({
      userId: ALICE_UID,
      memoryKey: "Bike Number",
      memoryValue: "UP32AB1234",
      category: "fact",
      approved: true,
      isPermanent: true,
      createdAt: past,
      updatedAt: past,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("alice_mem").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("alice_mem").update({
      memoryValue: "Tampered",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("alice_mem").delete());
});

test("Schema validation rejects tampered timestamps, ghost fields, extra @DocumentId id, and invalid enums", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 10000);
  const future = new Date(Date.now() + 60000);

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t_future").set({
      userId: ALICE_UID,
      title: "Future",
      notes: "",
      dueLabel: "",
      priority: "low",
      completed: false,
      createdAt: future,
      updatedAt: future,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t_id").set({
      id: "t_id",
      userId: ALICE_UID,
      title: "Extra id",
      notes: "",
      dueLabel: "",
      priority: "low",
      completed: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("m_enum").set({
      userId: ALICE_UID,
      memoryKey: "Secret",
      memoryValue: "1234",
      category: "invalid_cat",
      approved: true,
      isPermanent: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t_valid").set({
      userId: ALICE_UID,
      title: "Valid task",
      notes: "",
      dueLabel: "",
      priority: "medium",
      completed: false,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t_valid").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
