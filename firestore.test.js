const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_1";
const ADMIN_EMAIL = "yw3050699@gmail.com";
const PROCTOR_UID = "proctor_uid_2";
const STUDENT_UID = "student_uid_3";
const STUDENT_EMAIL = "siswa1@smpn1.sch.id";
const OTHER_UID = "other_uid_4";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

function spoofedAdminContext() {
  return testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  });
}

function proctorContext() {
  return testEnv.authenticatedContext(PROCTOR_UID, {
    email: "petugas@smpn1.sch.id",
    email_verified: true,
  });
}

function studentContext() {
  return testEnv.authenticatedContext(STUDENT_UID, {
    email: STUDENT_EMAIL,
    email_verified: true,
  });
}

function otherContext() {
  return testEnv.authenticatedContext(OTHER_UID, {
    email: "other@example.com",
    email_verified: true,
  });
}

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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("classes").get());
  await assertFails(unauthDb.collection("students").get());
  await assertFails(unauthDb.collection("exams").get());
  await assertFails(
    unauthDb.collection("users").doc("u1").set({
      userId: "u1",
      fullName: "Anon",
      email: "a@b.com",
      role: "student",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Email spoofing attack (unverified admin email) is rejected", async () => {
  const spoofDb = spoofedAdminContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(
    spoofDb.collection("classes").doc("cls_7a").set({
      name: "VII-A",
      gradeLevel: 7,
      homeroomTeacher: "Ibu Sari",
      capacity: 32,
      published: true,
      ownerId: "spoof_uid",
      staffUids: ["spoof_uid"],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("User registration prevents self-assigned admin/proctor roles and ghost fields", async () => {
  const studentDb = studentContext().firestore();
  const now = new Date(Date.now() - 1000);

  // Self-assigning admin role must fail
  await assertFails(
    studentDb.collection("users").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      fullName: "Budi",
      email: STUDENT_EMAIL,
      role: "admin",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Shadow field must fail
  await assertFails(
    studentDb.collection("users").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      fullName: "Budi",
      email: STUDENT_EMAIL,
      role: "student",
      isVerified: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid student registration succeeds
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      fullName: "Budi Santoso",
      email: STUDENT_EMAIL,
      role: "student",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student cannot escalate own role on update
  await assertFails(
    studentDb.collection("users").doc(STUDENT_UID).update({
      role: "admin",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Admin manages classes, subjects, students, exams, and questions; non-admin blocked", async () => {
  const adminDb = adminContext().firestore();
  const studentDb = studentContext().firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    adminDb.collection("classes").doc("cls_7a").set({
      name: "VII-A",
      gradeLevel: 7,
      homeroomTeacher: "Ibu Rina, S.Pd",
      capacity: 36,
      published: true,
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("subjects").doc("sub_mtk").set({
      code: "MTK",
      name: "Matematika",
      published: true,
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student cannot create student or class
  await assertFails(
    studentDb.collection("classes").doc("cls_hack").set({
      name: "IX-Z",
      gradeLevel: 9,
      homeroomTeacher: "Hacker",
      capacity: 30,
      published: true,
      ownerId: STUDENT_UID,
      staffUids: [STUDENT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("students").doc("stu_1").set({
      nis: "2026001",
      fullName: "Ahmad Fauzi",
      gender: "L",
      classId: "cls_7a",
      className: "VII-A",
      qrToken: "SMPN1-2026001-A1B2C3",
      linkedEmail: STUDENT_EMAIL,
      authUserId: "",
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student can claim own pre-linked student record, other user cannot
  await assertFails(
    otherContext().firestore().collection("students").doc("stu_1").update({
      authUserId: OTHER_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    studentDb.collection("students").doc("stu_1").update({
      authUserId: STUDENT_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Student query alignment: filtered by authUserId succeeds, unfiltered fails
  await assertSucceeds(
    studentDb.collection("students").where("authUserId", "==", STUDENT_UID).get()
  );
  await assertFails(otherContext().firestore().collection("students").get());
});

test("Proctor QR attendance prevents double check-in and validates references", async () => {
  const adminDb = adminContext().firestore();
  const proctorDb = proctorContext().firestore();
  const now = new Date(Date.now() - 10000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("proctors").doc(PROCTOR_UID).set({
      userId: PROCTOR_UID,
      email: "petugas@smpn1.sch.id",
      fullName: "Pak Hendra",
      grantedByUid: ADMIN_UID,
      createdAt: now,
    });
    await db.collection("classes").doc("cls_7a").set({
      name: "VII-A",
      gradeLevel: 7,
      homeroomTeacher: "Ibu Rina",
      capacity: 36,
      published: true,
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("subjects").doc("sub_mtk").set({
      code: "MTK",
      name: "Matematika",
      published: true,
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("students").doc("stu_1").set({
      nis: "2026001",
      fullName: "Ahmad Fauzi",
      gender: "L",
      classId: "cls_7a",
      className: "VII-A",
      qrToken: "SMPN1-2026001-A1B2C3",
      linkedEmail: STUDENT_EMAIL,
      authUserId: STUDENT_UID,
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("exams").doc("exam_1").set({
      title: "PAS Matematika VII",
      subjectId: "sub_mtk",
      subjectName: "Matematika",
      classIds: ["cls_7a"],
      classNames: ["VII-A"],
      startAt: new Date(Date.now() - 60000),
      endAt: new Date(Date.now() + 3600000),
      durationMinutes: 90,
      tokenCode: "MTK7A",
      published: true,
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  // Proctor records attendance once
  const attRef = proctorDb.collection("attendance").doc("exam_1_stu_1");
  await assertSucceeds(
    attRef.set({
      examId: "exam_1",
      examTitle: "PAS Matematika VII",
      studentId: "stu_1",
      studentName: "Ahmad Fauzi",
      nis: "2026001",
      className: "VII-A",
      qrToken: "SMPN1-2026001-A1B2C3",
      studentAuthUid: STUDENT_UID,
      scannedByUid: PROCTOR_UID,
      scannedByName: "Pak Hendra",
      status: "present",
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      scannedAt: new Date(Date.now() - 1000),
    })
  );

  // Duplicate update/overwrite is forbidden
  await assertFails(
    attRef.update({
      scannedAt: new Date(Date.now() - 500),
    })
  );

  // Student cannot forge attendance
  await assertFails(
    studentContext().firestore().collection("attendance").doc("exam_1_stu_2").set({
      examId: "exam_1",
      examTitle: "PAS Matematika VII",
      studentId: "stu_1",
      studentName: "Ahmad Fauzi",
      nis: "2026001",
      className: "VII-A",
      qrToken: "SMPN1-2026001-A1B2C3",
      studentAuthUid: STUDENT_UID,
      scannedByUid: STUDENT_UID,
      scannedByName: "Self",
      status: "present",
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      scannedAt: new Date(Date.now() - 1000),
    })
  );

  // Proctor filtered query succeeds
  await assertSucceeds(
    proctorDb.collection("attendance").where("staffUids", "array-contains", PROCTOR_UID).get()
  );
  // Admin query succeeds
  await assertSucceeds(adminDb.collection("attendance").get());
});

test("Exam submission autosave, submit lock, and proctor essay grading", async () => {
  const proctorDb = proctorContext().firestore();
  const studentDb = studentContext().firestore();
  const now = new Date(Date.now() - 10000);

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("proctors").doc(PROCTOR_UID).set({
      userId: PROCTOR_UID,
      email: "petugas@smpn1.sch.id",
      fullName: "Pak Hendra",
      grantedByUid: ADMIN_UID,
      createdAt: now,
    });
    await db.collection("students").doc("stu_1").set({
      nis: "2026001",
      fullName: "Ahmad Fauzi",
      gender: "L",
      classId: "cls_7a",
      className: "VII-A",
      qrToken: "SMPN1-2026001-A1B2C3",
      linkedEmail: STUDENT_EMAIL,
      authUserId: STUDENT_UID,
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("exams").doc("exam_1").set({
      title: "PAS Matematika VII",
      subjectId: "sub_mtk",
      subjectName: "Matematika",
      classIds: ["cls_7a"],
      classNames: ["VII-A"],
      startAt: new Date(Date.now() - 60000),
      endAt: new Date(Date.now() + 3600000),
      durationMinutes: 90,
      tokenCode: "MTK7A",
      published: true,
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const subRef = studentDb.collection("submissions").doc("exam_1_stu_1");
  await assertSucceeds(
    subRef.set({
      examId: "exam_1",
      examTitle: "PAS Matematika VII",
      subjectName: "Matematika",
      studentId: "stu_1",
      studentName: "Ahmad Fauzi",
      nis: "2026001",
      className: "VII-A",
      studentAuthUid: STUDENT_UID,
      answers: {},
      essayScores: {},
      autoScore: 0,
      essayScore: 0,
      totalScore: 0,
      maxScore: 100,
      status: "in_progress",
      ownerId: ADMIN_UID,
      staffUids: [ADMIN_UID, PROCTOR_UID],
      startedAt: now,
      updatedAt: now,
    })
  );

  // Student autosave while in_progress succeeds
  await assertSucceeds(
    subRef.update({
      answers: { q1: "A", q2: "Jawaban esai siswa" },
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  // Student submit transitions to submitted
  await assertSucceeds(
    subRef.update({
      answers: { q1: "A", q2: "Jawaban esai siswa final" },
      autoScore: 70,
      essayScore: 0,
      totalScore: 70,
      maxScore: 100,
      status: "submitted",
      submittedAt: new Date(Date.now() - 2000),
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Terminal state locked: student cannot edit answers after submit
  await assertFails(
    subRef.update({
      answers: { q1: "B" },
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Proctor grades essay
  await assertSucceeds(
    proctorDb.collection("submissions").doc("exam_1_stu_1").update({
      essayScores: { q2: 25 },
      essayScore: 25,
      totalScore: 95,
      status: "graded",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
