/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.deploy.liveedit;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.deploy.asm.Type;
import com.android.tools.deploy.interpreter.DoubleValue;
import com.android.tools.deploy.interpreter.Eval;
import com.android.tools.deploy.interpreter.FieldDescription;
import com.android.tools.deploy.interpreter.FloatValue;
import com.android.tools.deploy.interpreter.IntValue;
import com.android.tools.deploy.interpreter.InterpreterException;
import com.android.tools.deploy.interpreter.JNI;
import com.android.tools.deploy.interpreter.LongValue;
import com.android.tools.deploy.interpreter.MethodDescription;
import com.android.tools.deploy.interpreter.ObjectValue;
import com.android.tools.deploy.interpreter.Throw;
import com.android.tools.deploy.interpreter.Value;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class AndroidEval implements Eval {

    // Lookup table used to encode unboxing instructions when forwarding invokespecial to JNI.
    // This table *must* match what is in
    // tools/base/deploy/agent/native/interpreter/jni_interpreter.cc
    private static final int NO_UNBOXING = 0;
    private static final Map<Type, Integer> classToInst;
    static {
        Map<Type, Integer> classToInstBuilder = new HashMap<>();
        classToInstBuilder.put(Type.BOOLEAN_TYPE, 1 << 0);
        classToInstBuilder.put(Type.BYTE_TYPE, 1 << 1);
        classToInstBuilder.put(Type.CHAR_TYPE, 1 << 2);
        classToInstBuilder.put(Type.SHORT_TYPE, 1 << 3);
        classToInstBuilder.put(Type.INT_TYPE, 1 << 4);
        classToInstBuilder.put(Type.LONG_TYPE, 1 << 5);
        classToInstBuilder.put(Type.FLOAT_TYPE, 1 << 6);
        classToInstBuilder.put(Type.DOUBLE_TYPE, 1 << 7);
        classToInst = classToInstBuilder;
    }

    private final ClassLoader classloader;

    public AndroidEval(ClassLoader classloader) {
        this.classloader = classloader;
    }

    @NonNull
    @Override
    public Value getArrayElement(Value array, @NonNull Value index) {
        if (array.getAsmType().getDimensions() > 1) {
            // This is multi-dimension array. We return an object array with dimension -1.
            // e.g. [[I returns [I.
            String subDescriptor = array.getAsmType().getDescriptor().substring(1);
            Type type = Type.getType(subDescriptor);
            return new ObjectValue(Array.get(array.obj(), index.getInt()), type);
        }

        Type elementType = array.getAsmType().getElementType();
        return makeValue(Array.get(array.obj(), index.getInt()), elementType);
    }

    @NonNull
    @Override
    public Value getArrayLength(@NonNull Value array) {
        return new IntValue(Array.getLength(array.obj()), Type.INT_TYPE);
    }

    @NonNull
    @Override
    public Value getField(@NonNull Value value, FieldDescription description) {
        Object owner = value.obj();
        String name = description.getName();
        String type = description.getDesc();
        try {
            String ownerClass = description.getOwnerInternalName();
            Field field = findField(forName(ownerClass), name);
            field.setAccessible(true);
            return makeValue(field.get(owner), Type.getType(type));
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            throw new InterpreterException(e);
        }
    }

    @NonNull
    @Override
    public Value getStaticField(FieldDescription description) {
        String owner = description.getOwnerInternalName().replace('/', '.');
        String name = description.getName();
        String type = description.getDesc();
        try {

            Field field = findField(forName(owner), name);
            field.setAccessible(true);
            return makeValue(field.get(owner), Type.getType(type));
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            throw new InterpreterException(e);
        }
    }

    @NonNull
    @Override
    public Value invokeSpecial(
            @NonNull Value target,
            MethodDescription methodDesc,
            @NonNull List<? extends Value> argsValues) {
        try {
            String name = methodDesc.getName();
            String description = methodDesc.getDesc();
            Type[] parameterType = Type.getArgumentTypes(description);
            Class<?>[] parameterClass = new Class[parameterType.length];

            for (int i = 0; i < parameterClass.length; i++) {
                parameterClass[i] = typeToClass(parameterType[i]);
            }

            Object[] args = new Object[argsValues.size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = argsValues.get(i).obj(parameterType[i]);
            }

            ObjectValue objTarget = (ObjectValue) target;
            Class klass = forName(methodDesc.getOwnerInternalName());

            // This is a constructor call. We don't use invokespecial yet since we also need
            // to support ALLOC opcode to go along with it.
            if ("<init>".equals(name)) {
                if (objTarget.getValue() != null) {
                    // This is a call to super.<init> which we currently not handle.
                    throw new IllegalStateException("Unable to do super.<init>");
                }
                Constructor constructor = klass.getDeclaredConstructor(parameterClass);
                constructor.setAccessible(true);
                Object obj = constructor.newInstance(args);
                objTarget.setValue(obj);
                return new ObjectValue(obj, objTarget.getAsmType());
            }

            if (!(klass.isInstance(target.obj()))) {
                String f = "Cannot invokespecial '%s' on a '%s' (obj is '%s')";
                String targetObjClass = objTarget.obj().getClass().getCanonicalName();
                String m = String.format(f, methodDesc, klass, targetObjClass);
                throw new IllegalStateException(m);
            }

            // Build unboxing instructions so the jni does not have to reparse again the function
            // descriptor.
            int[] unbox = buildUnboxingInst(parameterType);

            // invokespecial towards super or private method
            Type returnType = Type.getReturnType(description);
            int type = returnType.getSort();
            switch (type) {
                case Type.VOID:
                    {
                        JNI.invokespecial(target.obj(), klass, name, description, args, unbox);
                        return makeValue(null, returnType);
                    }
                case Type.INT:
                    {
                        int i =
                                JNI.invokespecialI(
                                        target.obj(), klass, name, description, args, unbox);
                        return makeValue(i, returnType);
                    }
                case Type.SHORT:
                    {
                        short s =
                                JNI.invokespecialS(
                                        target.obj(), klass, name, description, args, unbox);
                        return makeValue(s, returnType);
                    }
                case Type.BOOLEAN:
                    {
                        boolean z =
                                JNI.invokespecialZ(
                                        target.obj(), klass, name, description, args, unbox);
                        return makeValue(z, returnType);
                    }
                case Type.CHAR:
                    {
                        char c =
                                JNI.invokespecialC(
                                        target.obj(), klass, name, description, args, unbox);
                        return makeValue(c, returnType);
                    }
                case Type.BYTE:
                    {
                        byte b =
                                JNI.invokespecialB(
                                        target.obj(), klass, name, description, args, unbox);
                        return makeValue(b, returnType);
                    }
                case Type.LONG:
                    {
                        long j =
                                JNI.invokespecialJ(
                                        target.obj(), klass, name, description, args, unbox);
                        return makeValue(j, returnType);
                    }
                case Type.FLOAT:
                    {
                        float f =
                                JNI.invokespecialF(
                                        target.obj(), klass, name, description, args, unbox);
                        return makeValue(f, returnType);
                    }
                case Type.DOUBLE:
                    {
                        double d =
                                JNI.invokespecialD(
                                        target.obj(), klass, name, description, args, unbox);
                        return makeValue(d, returnType);
                    }
                case Type.OBJECT:
                case Type.ARRAY:
                    {
                        Object o =
                                JNI.invokespecialL(
                                        target.obj(), klass, name, description, args, unbox);
                        return makeValue(o, returnType);
                    }
                default:
                    {
                        String fmt = "invokespecial error: Missing case for return type %d";
                        String msg = String.format(fmt, type);
                        throw new IllegalStateException(msg);
                    }
            }
        } catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException e) {
            throw new InterpreterException(e);
        } catch (NoSuchMethodException e) {
            // Make sure this method can be found
            String owner = methodDesc.getOwnerInternalName();
            String name = methodDesc.getName();
            String desc = methodDesc.getDesc();
            throw new IllegalStateException(methodNotFoundMsg(owner, name, desc));
        } catch (InvocationTargetException e) {
            Throw.sneaky(e.getCause());
        }
        throw new IllegalStateException("Reached end of invokespecial");
    }

    private static int[] buildUnboxingInst(Type[] types) {
        int[] inst = new int[types.length];
        for (int i = 0; i < types.length; i++) {
            inst[i] = classToInst.getOrDefault(types[i], NO_UNBOXING);
        }
        return inst;
    }

    @NonNull
    @Override
    public Value invokeInterface(
            @NonNull Value target,
            MethodDescription methodDesc,
            @NonNull List<? extends Value> args) {
        // In invokeinterface, Method lookup should not start from the method desc owner but from
        // the target object canonical name.
        String owner = target.obj().getClass().getName();
        MethodDescription md =
                new MethodDescription(owner, methodDesc.getName(), methodDesc.getDesc());
        return invokeMethod(target, md, args);
    }

    @NonNull
    @Override
    public Value invokeMethod(
            @NonNull Value target,
            MethodDescription methodDesc,
            @NonNull List<? extends Value> args) {
        String owner = methodDesc.getOwnerInternalName();
        String name = methodDesc.getName();
        String description = methodDesc.getDesc();
        Type[] parameterType = Type.getArgumentTypes(description);
        Type returnType = Type.getReturnType(description);
        try {
            Object[] argValues = new Object[args.size()];
            for (int i = 0; i < argValues.length; i++) {
                argValues[i] = args.get(i).obj(parameterType[i]);
            }

            Method method = methodLookup(owner, name, parameterType, returnType);
            if (method == null) {
                // Unlikely since we know that the class compiles.
                throw new IllegalStateException(methodNotFoundMsg(owner, name, description));
            }

            method.setAccessible(true);
            Object result = method.invoke(target.obj(), argValues);
            return makeValue(result, Type.getReturnType(method));
        } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException e) {
            throw new InterpreterException(e);
        } catch (InvocationTargetException e) {
            Throw.sneaky(e.getCause());
        }
        throw new IllegalStateException();
    }

    @NonNull
    @Override
    public Value invokeStaticMethod(
            MethodDescription description, @NonNull List<? extends Value> args) {
        String owner = description.getOwnerInternalName();
        String methodName = description.getName();
        String signature = description.getDesc();
        Type[] parameterType = Type.getArgumentTypes(signature);
        Type returnType = Type.getReturnType(signature);
        try {
            Object[] argValues = new Object[args.size()];
            for (int i = 0; i < argValues.length; i++) {
                argValues[i] = args.get(i).obj(parameterType[i]);
            }

            // Static method are inherited, the lookup must be recursive starting from the owner.
            Method method = methodLookup(owner, methodName, parameterType, returnType);
            if (method == null) {
                // Unlikely since we know that the class compiles.
                throw new IllegalStateException(methodNotFoundMsg(owner, methodName, signature));
            }

            method.setAccessible(true);
            Object result = method.invoke(null, argValues);
            return makeValue(result, Type.getReturnType(method));
        } catch (ClassNotFoundException | IllegalAccessException | IllegalArgumentException e) {
            throw new InterpreterException(e);
        } catch (InvocationTargetException e) {
            Throw.sneaky(e.getCause());
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean isInstanceOf(@NonNull Value target, @NonNull Type type) {
        try {
            Class<?> c = typeToClass(type);
            return c.isInstance(target.obj());
        } catch (ClassNotFoundException e) {
            throw new InterpreterException(e);
        }
    }

    @NonNull
    @Override
    public Value loadClass(@NonNull Type type) {
        try {
            Class<?> c = typeToClass(type);
            return new ObjectValue(c, Type.getObjectType("java/lang/Class"));
        } catch (ClassNotFoundException e) {
            throw new InterpreterException(e);
        }
    }

    @NonNull
    @Override
    public Value loadString(@NonNull String s) {
        return new ObjectValue(s, Type.getObjectType("java/lang/String"));
    }

    @NonNull
    @Override
    public Value newArray(Type type, int length) {
        try {
            // ASM's getElementType of a multidimensional array returns the type of
            // the element of zero dimension, not the type of an entry within that array.
            // IE int[][][] -> int
            Class<?> elementClass = typeToClass(type.getElementType());

            // If this is a multidimensional array, we need to created a class type of array
            // of elementClass of dimension minus 1.
            for (int dim = type.getDimensions() - 1; dim != 0; dim--) {

                // There doesn't seem to be Java API to fetch an array type of a given class type.
                // We can only create a temp array and call getClass() on it.
                elementClass = Array.newInstance(elementClass, 0).getClass();
            }
            return makeValue(Array.newInstance(elementClass, length), type);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    @Override
    public Value newInstance(@NonNull Type type) {
        return new ObjectValue(null, type);
    }

    @NonNull
    @Override
    public Value newMultiDimensionalArray(Type type, List<Integer> dimensions) {
        try {
            Class<?> elementClass = typeToClass(type.getElementType());
            return makeValue(
                    Array.newInstance(elementClass, dimensions.stream().mapToInt(e -> e).toArray()),
                    type);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setArrayElement(Value array, Value index, @NonNull Value newValue) {
        Type elementType = array.getAsmType().getElementType();
        Object arrayObject = array.obj();
        int arrayIndex = index.getInt();

        // This is a multi-dimension array for which the incoming value MUST be an object.
        if (array.getAsmType().getDimensions() > 1) {
            Array.set(arrayObject, arrayIndex, ((ObjectValue) newValue).getValue());
        } else {
            Array.set(arrayObject, arrayIndex, newValue.obj(elementType));
        }
    }

    @Override
    public void setField(@NonNull Value owner, FieldDescription description, Value value) {
        String ownerClass = description.getOwnerInternalName().replace('/', '.');
        String name = description.getName();
        try {
            Field field = findField(forName(ownerClass), name);
            field.setAccessible(true);
            Type expectedType = Type.getType(description.getDesc());
            field.set(owner.obj(), value.obj(expectedType));
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            throw new InterpreterException(e);
        }
    }

    @Override
    public void setStaticField(FieldDescription description, @NonNull Value value) {
        String ownerClassName = description.getOwnerInternalName().replace('/', '.');
        String name = description.getName();
        try {
            Class<?> ownerClass = forName(ownerClassName);
            Field field = findField(ownerClass, name);
            field.setAccessible(true);
            Type expectedType = Type.getType(description.getDesc());
            field.set(ownerClass, value.obj(expectedType));
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
            throw new InterpreterException(e);
        }
    }

    @Override
    public void monitorEnter(@NonNull Value value) {
        JNI.enterMonitor(value.obj());
    }

    @Override
    public void monitorExit(@NonNull Value value) {
        JNI.exitMonitor(value.obj());
    }

    public static Value makeValue(Object v, Type type) {
        switch (type.getSort()) {
            case Type.INT:
                return new IntValue((Integer) v, type);
            case Type.BOOLEAN:
                return new IntValue((Boolean) v ? 1 : 0, type);
            case Type.BYTE:
                return new IntValue(((Byte) v).intValue(), type);
            case Type.SHORT:
                return new IntValue(((Short) v).intValue(), type);
            case Type.CHAR:
                return new IntValue((Character) v, type);
            case Type.LONG:
                return new LongValue((Long) v);
            case Type.FLOAT:
                return new FloatValue((Float) v);
            case Type.DOUBLE:
                return new DoubleValue((Double) v);
            case Type.VOID:
                return new IntValue(0, type);
            default:
                return new ObjectValue(v, type);
        }
    }

    Class<?> forName(String className) throws ClassNotFoundException {
        return Class.forName(className.replace('/', '.'), true, classloader);
    }

    private boolean isVisible(@NonNull Field f, boolean allowPrivate) {
        if (allowPrivate) {
            return true;
        }
        return !Modifier.isPrivate(f.getModifiers());
    }

    @Nullable
    private Field searchField(@NonNull Class clazz, @NonNull String name) {
        return searchField(clazz, name, true);
    }

    @Nullable
    private Field searchField(@NonNull Class clazz, @NonNull String name, boolean allowPrivate) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field f : fields) {
            if (f.getName().equals(name) && isVisible(f, allowPrivate)) {
                return f;
            }
        }
        return null;
    }

    // Recursively find all interfaces in the inheritance chain of the interface.
    // Because there can be cases where an interface is implemented twice (once directly and
    // then indirectly, the list may contain duplicates). e.g.: iZ is implemented by both cX
    // and cY, and cY implements iZ and extends cX.
    @NonNull
    private List<Class> findAllInterfaces(@NonNull Class interfazz) {
        List<Class> result = new ArrayList<>();
        Class[] interfaces = interfazz.getInterfaces();

        for (Class i : interfaces) {
            result.addAll(findAllInterfaces(i));
        }
        result.addAll(Arrays.asList(interfazz.getInterfaces()));
        return result;
    }

    // Neither getFields not getDeclaredFields do what we want.
    // - getFields returns all PUBLIC field of the current class (including hierarchy)
    // - getDeclaredFields returns public/protected/final of the current Class (no hierarchy
    // lookup).
    //
    // We want to find public/protected fields (+private for starting class), while walking the
    // whole hierarchy up to Object.
    private Field findField(Class clazz, String name) throws NoSuchFieldException {
        String className = clazz.getCanonicalName();
        Field field;

        // Only true for the first iteration in the loop (the lookup start class).
        boolean allowPrivate = true;

        while (clazz != null) {

            // First try to lookup the current class
            field = searchField(clazz, name, allowPrivate);
            if (field != null) {
                return field;
            }

            // Check if it is in an interface or its ancestor interfaces
            List<Class> interfaces = findAllInterfaces(clazz);
            for (Class interfazz : interfaces) {
                field = searchField(interfazz, name);
                if (field != null) {
                    return field;
                }
            }

            // We are recursing. From here we don't allow private field to be found.
            // This was only allowed in the start class
            allowPrivate = false;

            clazz = clazz.getSuperclass();
        }
        throw new NoSuchFieldException("Can't find '" + name + "' in class '" + className + "'");
    }

    public Class<?> typeToClass(Type type) throws ClassNotFoundException {
        switch (type.getSort()) {
            case Type.INT:
                return int.class;
            case Type.BOOLEAN:
                return boolean.class;
            case Type.BYTE:
                return byte.class;
            case Type.SHORT:
                return short.class;
            case Type.CHAR:
                return char.class;
            case Type.LONG:
                return long.class;
            case Type.FLOAT:
                return float.class;
            case Type.DOUBLE:
                return double.class;
            case Type.VOID:
                return void.class;
            case Type.ARRAY:
                return forName(type.getDescriptor());
            default:
                return forName(type.getClassName());
        }
    }

    protected Method methodLookup(
            String className, String methodName, Type[] parameterType, Type returnType)
            throws ClassNotFoundException {
        List<Method> methods = getAllDeclaredMethods(className);
        for (Method method : methods) {
            if (method.getName().equals(methodName)
                    && Type.getReturnType(method).equals(returnType)
                    && Arrays.equals(Type.getArgumentTypes(method), parameterType)) {
                return method;
            }
        }
        return null;
    }

    private String methodNotFoundMsg(String className, String method, String desc) {
        List<Method> foundMethods;
        List<Constructor> foundConstructors;
        try {
            foundMethods = getAllDeclaredMethods(className);
            foundConstructors = getAllDeclaredConstructors(className);
        } catch (ClassNotFoundException e) {
            return "Cannot find class '" + className + "'";
        }
        String methodName = method + desc;
        StringBuilder msg =
                new StringBuilder("Cannot find '" + methodName + "' in " + className + "\n");

        msg.append("Found constructors:\n");
        for (Constructor c : foundConstructors) {
            msg.append("    '");
            msg.append(c.getDeclaringClass());
            msg.append(".");
            msg.append("<init>");
            msg.append(Arrays.toString(c.getParameterTypes()));
            msg.append("\n");
        }

        msg.append("Found methods:\n");
        for (Method m : foundMethods) {
            msg.append("    '");
            msg.append(m.getDeclaringClass());
            msg.append(".");
            msg.append(m.getName());
            msg.append(Arrays.toString(m.getParameterTypes()));
            msg.append(m.getReturnType());
            msg.append("\n");
        }
        return msg.toString();
    }

    // Neither Class.getMethods nor Class.getAllDeclaredMethods can do what we need.
    // We want to search all inherited methods (including from interfaces), whether
    // they are public, protected, or private.
    // * Class.getMethods returns everything inherited (including interfaces ) for the given Class
    // but
    // only what is public.
    // * Class.getDeclaredMethods returns private/public/protected for the given Class.
    //
    // This is a mix of both which recursively walks the inheritance chain and interfaces to capture
    // all methods
    // regardless of their visibility.
    private List<Method> getAllDeclaredMethods(String className) throws ClassNotFoundException {
        List<Method> methods = new ArrayList<>();

        Class<?> clazz = forName(className.replace('/', '.'));
        LinkedList<Class<?>> queue = new LinkedList<>();
        queue.add(clazz);

        HashSet<Class<?>> visitedInterfaces = new HashSet<>();
        while (queue.size() > 0) {
            Class<?> clz = queue.remove();
            Collections.addAll(methods, clz.getDeclaredMethods());

            Class<?> superclass = clz.getSuperclass();
            if (superclass != null) {
                queue.add(superclass);
            }
            Class<?>[] interfaces = clz.getInterfaces();
            for (Class<?> inter : interfaces) {
                if (!visitedInterfaces.contains(inter)) {
                    queue.add(inter);
                    visitedInterfaces.add(inter);
                }
            }
        }
        return methods;
    }

    private List<Constructor> getAllDeclaredConstructors(String className)
            throws ClassNotFoundException {
        List<Constructor> constructors = new ArrayList<>();
        Class<?> clazz = forName(className.replace('/', '.'));
        constructors.addAll(Arrays.asList(clazz.getDeclaredConstructors()));
        // Also add constructor available from super. But don't recurse over the whole chain of
        // inheritance.
        if (clazz.getSuperclass() != null) {
            constructors.addAll(Arrays.asList(clazz.getSuperclass().getDeclaredConstructors()));
        }
        return constructors;
    }
}
