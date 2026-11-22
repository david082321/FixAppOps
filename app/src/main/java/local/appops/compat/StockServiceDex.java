package local.appops.compat;

import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.MethodParameter;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;
import org.jf.dexlib2.iface.instruction.formats.Instruction3rc;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.ImmutableMethodImplementation;
import org.jf.dexlib2.immutable.ImmutableMethodParameter;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction12x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.writer.io.MemoryDataStore;
import org.jf.dexlib2.writer.pool.DexPool;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Four build-1708 classes used only by the separately authorized compatibility service. */
final class StockServiceDex {
    private static final byte[] DIGEST = HexFormat.of().parseHex(
            "e536de78cbf62fb4edab127ab1fd5aa3f32c6a857d47f0a12ab5cd8c056c27f0");
    private static final String FLAGS = "Lrikka/appops/ak0;";
    private static final String SYNC = "Lrikka/appops/pb;";
    private static final String ACTIVE = "Lrikka/appops/u9;";
    private static final String NOTED = "Lrikka/appops/hb;";
    private static final String MANAGER = "Landroid/permission/IPermissionManager;";
    private static final String ADAPTER = "Llocal/appops/compat/PermissionCompat;";
    private static final String STRING = "Ljava/lang/String;";
    private static final Set<String> CLASSES = Set.of(FLAGS, SYNC, ACTIVE, NOTED);

    private StockServiceDex() {}

    static byte[] adapt(byte[] pinnedOriginal, boolean adaptPermissions) throws IOException {
        try {
            if (!MessageDigest.isEqual(DIGEST, MessageDigest.getInstance("SHA-256").digest(pinnedOriginal))) {
                throw new IllegalArgumentException("Unsupported App Ops classes.dex");
            }
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        Opcodes opcodes = Opcodes.forDexVersion(37);
        DexBackedDexFile original = new DexBackedDexFile(opcodes, pinnedOriginal);
        DexPool output = new DexPool(opcodes);
        for (ClassDef type : original.getClasses()) {
            if (!CLASSES.contains(type.getType())) continue;
            // Let the installed APK supply working permission implementations on early Android 14.
            if (!adaptPermissions && (type.getType().equals(FLAGS) || type.getType().equals(SYNC))) continue;
            List<Method> methods = new ArrayList<>();
            for (Method method : type.getMethods()) {
                methods.add(adaptPermissionCalls(method));
                if (isLegacyCallback(method)) methods.add(addDeviceArgument(method));
            }
            output.internClass(new ImmutableClassDef(type.getType(), type.getAccessFlags(), type.getSuperclass(),
                    type.getInterfaces(), type.getSourceFile(), type.getAnnotations(), type.getFields(), methods));
        }
        MemoryDataStore bytes = new MemoryDataStore();
        output.writeTo(bytes);
        return bytes.getData();
    }

    private static Method adaptPermissionCalls(Method method) {
        Set<String> needed;
        if (method.getDefiningClass().equals(FLAGS) && method.getName().equals("尾巴捏捏")) {
            needed = Set.of("getPermissionFlags");
        } else if (method.getDefiningClass().equals(FLAGS) && method.getName().equals("右舷鱼雷接近")) {
            needed = Set.of("updatePermissionFlags");
        } else if (method.getDefiningClass().equals(SYNC) && method.getName().equals("右舷鱼雷接近")) {
            needed = Set.of("grantRuntimePermission", "revokeRuntimePermission");
        } else {
            return method;
        }
        MethodImplementation old = method.getImplementation();
        List<Instruction> code = new ArrayList<>();
        Set<String> remaining = new HashSet<>(needed);
        for (Instruction instruction : old.getInstructions()) {
            // The first matching call is the SDK 31+ branch, with (package, permission, ...).
            // Lower API branches and the original OEM revoke fallback must retain their own ABI.
            if (instruction instanceof ReferenceInstruction ref && ref.getReference() instanceof MethodReference call
                    && call.getDefiningClass().equals(MANAGER) && remaining.remove(call.getName())) {
                instruction = callAdapter(instruction, call);
            }
            code.add(instruction);
        }
        if (!remaining.isEmpty()) throw new IllegalStateException("Missing stock permission call " + remaining);
        // The static invoke uses the receiver as its first input and has the same size as the old invoke.
        // Branch offsets, try ranges, handlers, debug addresses and the original service predicates stay valid.
        MethodImplementation body = new ImmutableMethodImplementation(old.getRegisterCount(), code,
                old.getTryBlocks(), old.getDebugItems());
        return new ImmutableMethod(method.getDefiningClass(), method.getName(), method.getParameters(),
                method.getReturnType(), method.getAccessFlags(), method.getAnnotations(), method.getHiddenApiRestrictions(), body);
    }

    private static Instruction callAdapter(Instruction instruction, MethodReference call) {
        List<CharSequence> parameters = new ArrayList<>();
        parameters.add("Ljava/lang/Object;");
        parameters.addAll(call.getParameterTypes());
        MethodReference replacement = new ImmutableMethodReference(ADAPTER, call.getName(), parameters, call.getReturnType());
        if (instruction instanceof Instruction35c op && op.getOpcode() == Opcode.INVOKE_INTERFACE) {
            return new ImmutableInstruction35c(Opcode.INVOKE_STATIC, op.getRegisterCount(), op.getRegisterC(),
                    op.getRegisterD(), op.getRegisterE(), op.getRegisterF(), op.getRegisterG(), replacement);
        }
        if (instruction instanceof Instruction3rc op && op.getOpcode() == Opcode.INVOKE_INTERFACE_RANGE) {
            return new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, op.getStartRegister(), op.getRegisterCount(), replacement);
        }
        throw new IllegalStateException("Unexpected permission invoke " + instruction.getOpcode());
    }

    private static boolean isLegacyCallback(Method method) {
        return (method.getDefiningClass().equals(ACTIVE) && method.getName().equals("opActiveChanged")
                && method.getParameterTypes().equals(List.of("I", "I", STRING, STRING, "Z", "I", "I")))
                || (method.getDefiningClass().equals(NOTED) && method.getName().equals("opNoted")
                && method.getParameterTypes().equals(List.of("I", "I", STRING, STRING, "I", "I")));
    }

    private static Method addDeviceArgument(Method legacy) {
        List<MethodParameter> parameters = new ArrayList<>(legacy.getParameters());
        parameters.add(4, new ImmutableMethodParameter("I", null, "virtualDeviceId"));
        int registerCount = 1 + parameters.size();
        List<Instruction> code = new ArrayList<>();
        // Registers 0..4 contain receiver, op, uid, package and attribution tag. Discard the
        // new device argument and forward every old parameter, including mode/attribution flags.
        for (int dest = 5; dest < registerCount - 1; dest++) {
            code.add(new ImmutableInstruction12x(Opcode.MOVE, dest, dest + 1));
        }
        code.add(new ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 0, registerCount - 1, legacy));
        code.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        return new ImmutableMethod(legacy.getDefiningClass(), legacy.getName(), parameters, "V",
                AccessFlags.PUBLIC.getValue(), null, null,
                new ImmutableMethodImplementation(registerCount, code, null, null));
    }
}
