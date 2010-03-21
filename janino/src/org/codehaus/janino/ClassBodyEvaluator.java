
/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2010, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. The name of the author may not be used to endorse or promote products derived from this software without
 *       specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.codehaus.janino;

import java.io.*;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.Cookable;
import org.codehaus.commons.compiler.IClassBodyEvaluator;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.ParseException;
import org.codehaus.commons.compiler.ScanException;
import org.codehaus.janino.util.enumerator.EnumeratorSet;

/**
 * The <code>optionalClassLoader</code> serves two purposes:
 * <ul>
 *   <li>It is used to look for classes referenced by the class body.
 *   <li>It is used to load the generated Java<sup>TM</sup> class
 *   into the JVM; directly if it is a subclass of {@link
 *   ByteArrayClassLoader}, or by creation of a temporary
 *   {@link ByteArrayClassLoader} if not.
 * </ul>
 * To set up a {@link ClassBodyEvaluator} object, proceed as follows:
 * <ol>
 *   <li>
 *   Create the {@link ClassBodyEvaluator} using {@link #ClassBodyEvaluator()}
 *   <li>
 *   Configure the {@link ClassBodyEvaluator} by calling any of the following methods:
 *   <ul>
 *      <li>{@link org.codehaus.janino.SimpleCompiler#setParentClassLoader(ClassLoader)}
 *      <li>{@link #setDefaultImports(String[])}
 *   </ul>
 *   <li>
 *   Call any of the {@link org.codehaus.commons.compiler.Cookable#cook(Scanner)} methods to scan,
 *   parse, compile and load the class body into the JVM.
 * </ol>
 * Alternatively, a number of "convenience constructors" exist that execute the steps described
 * above instantly.
 * <p>
 * To compile a class body and immediately instantiate an object, one of the
 * {@link #createFastClassBodyEvaluator(Scanner, Class, ClassLoader)} methods can be used.
 * <p>
 * The generated class may optionally extend/implement a given type; the returned instance can
 * safely be type-casted to that <code>optionalBaseType</code>.
 * <p>
 * Example:
 * <pre>
 * public interface Foo {
 *     int bar(int a, int b);
 * }
 * ...
 * Foo f = (Foo) ClassBodyEvaluator.createFastClassBodyEvaluator(
 *     new Scanner(null, new StringReader("public int bar(int a, int b) { return a + b; }")),
 *     Foo.class,                  // Base type to extend/implement
 *     (ClassLoader) null          // Use current thread's context class loader
 * );
 * System.out.println("1 + 2 = " + f.bar(1, 2));
 * </pre>
 * Notice: The <code>optionalBaseType</code> must be accessible from the generated class,
 * i.e. it must either be declared <code>public</code>, or with default accessibility in the
 * same package as the generated class.
 */
public class ClassBodyEvaluator extends SimpleCompiler implements IClassBodyEvaluator {
    protected static final Class[] ZERO_CLASSES = new Class[0];

    private String[]               optionalDefaultImports = null;
    protected String               className = IClassBodyEvaluator.DEFAULT_CLASS_NAME;
    private Class                  optionalExtendedType = null;
    private Class[]                implementedTypes = ClassBodyEvaluator.ZERO_CLASSES;
    private Class                  result = null; // null=uncooked

    /**
     * Equivalent to<pre>
     * ClassBodyEvaluator cbe = new ClassBodyEvaluator();
     * cbe.cook(classBody);</pre>
     *
     * @see #ClassBodyEvaluator()
     * @see Cookable#cook(String)
     */
    public ClassBodyEvaluator(
        String classBody
    ) throws CompileException, ParseException, ScanException {
        this.cook(classBody);
    }

    /**
     * Equivalent to<pre>
     * ClassBodyEvaluator cbe = new ClassBodyEvaluator();
     * cbe.cook(optionalFileName, is);</pre>
     *
     * @see #ClassBodyEvaluator()
     * @see Cookable#cook(String, InputStream)
     */
    public ClassBodyEvaluator(
        String      optionalFileName,
        InputStream is
    ) throws CompileException, ParseException, ScanException, IOException {
        this.cook(optionalFileName, is);
    }

    /**
     * Equivalent to<pre>
     * ClassBodyEvaluator cbe = new ClassBodyEvaluator();
     * cbe.cook(optionalFileName, reader);</pre>
     *
     * @see #ClassBodyEvaluator()
     * @see Cookable#cook(String, Reader)
     */
    public ClassBodyEvaluator(
        String   optionalFileName,
        Reader   reader
    ) throws CompileException, ParseException, ScanException, IOException {
        this.cook(optionalFileName, reader);
    }

    /**
     * Equivalent to<pre>
     * ClassBodyEvaluator cbe = new ClassBodyEvaluator();
     * cbe.setParentClassLoader(optionalParentClassLoader);
     * cbe.cook(scanner);</pre>
     *
     * @see #ClassBodyEvaluator()
     * @see SimpleCompiler#setParentClassLoader(ClassLoader)
     * @see Cookable#cook(Scanner)
     */
    public ClassBodyEvaluator(
        Scanner     scanner,
        ClassLoader optionalParentClassLoader
    ) throws CompileException, ParseException, ScanException, IOException {
        this.setParentClassLoader(optionalParentClassLoader);
        this.cook(scanner);
    }

    /**
     * Equivalent to<pre>
     * ClassBodyEvaluator cbe = new ClassBodyEvaluator();
     * cbe.setExtendedType(optionalExtendedType);
     * cbe.setImplementedTypes(implementedTypes);
     * cbe.setParentClassLoader(optionalParentClassLoader);
     * cbe.cook(scanner);</pre>
     *
     * @see #ClassBodyEvaluator()
     * @see #setExtendedClass(Class)
     * @see #setImplementedInterfaces(Class[])
     * @see SimpleCompiler#setParentClassLoader(ClassLoader)
     * @see Cookable#cook(Scanner)
     */
    public ClassBodyEvaluator(
        Scanner     scanner,
        Class       optionalExtendedType,
        Class[]     implementedTypes,
        ClassLoader optionalParentClassLoader
    ) throws CompileException, ParseException, ScanException, IOException {
        this.setExtendedClass(optionalExtendedType);
        this.setImplementedInterfaces(implementedTypes);
        this.setParentClassLoader(optionalParentClassLoader);
        this.cook(scanner);
    }

    /**
     * Equivalent to<pre>
     * ClassBodyEvaluator cbe = new ClassBodyEvaluator();
     * cbe.setClassName(className);
     * cbe.setExtendedType(optionalExtendedType);
     * cbe.setImplementedTypes(implementedTypes);
     * cbe.setParentClassLoader(optionalParentClassLoader);
     * cbe.cook(scanner);</pre>
     *
     * @see #ClassBodyEvaluator()
     * @see #setClassName(String)
     * @see #setExtendedClass(Class)
     * @see #setImplementedInterfaces(Class[])
     * @see SimpleCompiler#setParentClassLoader(ClassLoader)
     * @see Cookable#cook(Scanner)
     */
    public ClassBodyEvaluator(
        Scanner     scanner,
        String      className,
        Class       optionalExtendedType,
        Class[]     implementedTypes,
        ClassLoader optionalParentClassLoader
    ) throws CompileException, ParseException, ScanException, IOException {
        this.setClassName(className);
        this.setExtendedClass(optionalExtendedType);
        this.setImplementedInterfaces(implementedTypes);
        this.setParentClassLoader(optionalParentClassLoader);
        this.cook(scanner);
    }

    public ClassBodyEvaluator() {}

    public void setDefaultImports(String[] optionalDefaultImports) {
        assertNotCooked();
        this.optionalDefaultImports = optionalDefaultImports;
    }

    public void setClassName(String className) {
        if (className == null) throw new NullPointerException();
        assertNotCooked();
        this.className = className;
    }

    public void setExtendedClass(Class optionalExtendedType) {
        assertNotCooked();
        this.optionalExtendedType = optionalExtendedType;
    }

    /** @deprecated */
    public void setExtendedType(Class optionalExtendedClass) {
        this.setExtendedClass(optionalExtendedClass);
    }

    public void setImplementedInterfaces(Class[] implementedTypes) {
        if (implementedTypes == null) throw new NullPointerException("Zero implemented types must be specified as \"new Class[0]\", not \"null\"");
        assertNotCooked();
        this.implementedTypes = implementedTypes;
    }

    /** @deprecated */
    public void setImplementedTypes(Class[] implementedInterfaces) {
        this.setImplementedTypes(implementedInterfaces);
    }

    public void cook(Scanner scanner) throws CompileException, ParseException, ScanException, IOException {
        this.setUpClassLoaders();

        Java.CompilationUnit compilationUnit = this.makeCompilationUnit(scanner);

        // Add class declaration.
        Java.ClassDeclaration cd = this.addPackageMemberClassDeclaration(
            scanner.location(),
            compilationUnit
        );

        // Parse class body declarations (member declarations) until EOF.
        Parser parser = new Parser(scanner);
        while (!scanner.peek().isEOF()) {
            parser.parseClassBodyDeclaration(cd);
        }

        // Compile and load it.
        this.result = this.compileToClass(
            compilationUnit,              // compilationUnit
            DebuggingInformation.ALL,     // debuggingInformation
            this.className
        );
    }

    /**
     * Create a {@link Java.CompilationUnit}, set the default imports, and parse the import
     * declarations.
     * <p>
     * If the <code>optionalScanner</code> is given, a sequence of IMPORT directives is parsed
     * from it and added to the compilation unit.
     */
    protected final Java.CompilationUnit makeCompilationUnit(
        Scanner optionalScanner
    ) throws ParseException, ScanException, IOException {
        Java.CompilationUnit cu = new Java.CompilationUnit(optionalScanner == null ? null : optionalScanner.getFileName());

        // Set default imports.
        if (this.optionalDefaultImports != null) {
            for (int i = 0; i < this.optionalDefaultImports.length; ++i) {
                Scanner s = new Scanner(null, new StringReader(this.optionalDefaultImports[i]));
                cu.addImportDeclaration(new Parser(s).parseImportDeclarationBody());
                if (!s.peek().isEOF()) throw new ParseException("Unexpected token \"" + s.peek() + "\" in default import", s.location());
            }
        }

        // Parse all available IMPORT declarations.
        if (optionalScanner != null) {
            Parser parser = new Parser(optionalScanner);
            while (optionalScanner.peek().isKeyword("import")) {
                cu.addImportDeclaration(parser.parseImportDeclaration());
            }
        }

        return cu;
    }

    /**
     * To the given {@link Java.CompilationUnit}, add
     * <ul>
     *   <li>A class declaration with the configured name, superclass and interfaces
     *   <li>A method declaration with the given return type, name, parameter
     *       names and values and thrown exceptions
     * </ul>
     *
     * @return The created {@link Java.ClassDeclaration} object
     */
    protected Java.PackageMemberClassDeclaration addPackageMemberClassDeclaration(
        Location             location,
        Java.CompilationUnit compilationUnit
    ) throws ParseException {
        String cn = this.className;
        int idx = cn.lastIndexOf('.');
        if (idx != -1) {
            compilationUnit.setPackageDeclaration(new Java.PackageDeclaration(location, cn.substring(0, idx)));
            cn = cn.substring(idx + 1);
        }
        Java.PackageMemberClassDeclaration tlcd = new Java.PackageMemberClassDeclaration(
            location,                                              // location
            null,                                                  // optionalDocComment
            Mod.PUBLIC,                                            // modifiers
            cn,                                                    // name
            this.classToType(location, this.optionalExtendedType), // optionalExtendedType
            this.classesToTypes(location, this.implementedTypes)   // implementedTypes
        );
        compilationUnit.addPackageMemberTypeDeclaration(tlcd);
        return tlcd;
    }

    /**
     * Compile the given compilation unit, load all generated classes, and
     * return the class with the given name.
     * @param compilationUnit
     * @param debuggingInformation TODO
     * @param newClassName The fully qualified class name
     * @return The loaded class
     */
    protected final Class compileToClass(
        Java.CompilationUnit compilationUnit,
        EnumeratorSet        debuggingInformation,
        String               newClassName
    ) throws CompileException {

        // Compile and load the compilation unit.
        ClassLoader cl = this.compileToClassLoader(compilationUnit, debuggingInformation);

        // Find the generated class by name.
        try {
            return cl.loadClass(newClassName);
        } catch (ClassNotFoundException ex) {
            throw new JaninoRuntimeException("SNO: Generated compilation unit does not declare class \"" + newClassName + "\"");
        }
    }

    public Class getClazz() {
        if (this.getClass() != ClassBodyEvaluator.class) throw new IllegalStateException("Must not be called on derived instances");
        if (this.result == null) throw new IllegalStateException("Must only be called after \"cook()\"");
        return this.result;
    }

    public Object createInstance(Reader reader) throws CompileException, ParseException, ScanException, IOException {
        this.cook(reader);

        try {
            return this.getClazz().newInstance();
        } catch (InstantiationException ie) {
            CompileException ce = new CompileException("Class is abstract, an interface, an array class, a primitive type, or void; or has no zero-parameter constructor", null);
            ce.initCause(ie);
            throw ce;
        } catch (IllegalAccessException iae) {
            CompileException ce = new CompileException("The class or its zero-parameter constructor is not accessible", null);
            ce.initCause(iae);
            throw ce;
        }
    }

    /**
     * Use {@link #createInstance(Reader)} instead:
     * <pre>
     * IClassBodyEvaluator cbe = {@link CompilerFactoryFactory}.{@link
     * CompilerFactoryFactory#getDefaultCompilerFactory() getDefaultCompilerFactory}().{@link
     * ICompilerFactory#newClassBodyEvaluator() newClassBodyEvaluator}();
     * if (optionalBaseType != null) {
     *     if (optionalBaseType.isInterface()) {
     *         cbe.{@link #setImplementedInterfaces(Class[]) setImplementedInterfaces}(new Class[] { optionalBaseType });
     *     } else {
     *         cbe.{@link #setExtendedClass(Class) setExtendedClass}(optionalBaseType);
     *     }
     * }
     * cbe.{@link #setParentClassLoader(ClassLoader) setParentClassLoader}(optionalParentClassLoader);
     * cbe.{@link IClassBodyEvaluator#createInstance(Reader) createInstance}(reader);
     * </pre>
     *
     * @see #createInstance(Reader)
     */
    public static Object createFastClassBodyEvaluator(
        Scanner     scanner,
        Class       optionalBaseType,
        ClassLoader optionalParentClassLoader
    ) throws CompileException, ParseException, ScanException, IOException {
        return ClassBodyEvaluator.createFastClassBodyEvaluator(
            scanner,                                // scanner
            IClassBodyEvaluator.DEFAULT_CLASS_NAME, // className
            (                                       // optionalExtendedType
                optionalBaseType != null && !optionalBaseType.isInterface() ?
                optionalBaseType : null
            ),
            (                                       // implementedTypes
                optionalBaseType != null && optionalBaseType.isInterface() ?
                new Class[] { optionalBaseType } : new Class[0]
            ),
            optionalParentClassLoader               // optionalParentClassLoader
        );
    }

    /**
     * Use {@link #createInstance(Reader)} instead:
     * <pre>
     * IClassBodyEvaluator cbe = {@link CompilerFactoryFactory}.{@link
     * CompilerFactoryFactory#getDefaultCompilerFactory() getDefaultCompilerFactory}().{@link
     * ICompilerFactory#newClassBodyEvaluator() newClassBodyEvaluator}();
     * cbe.{@link #setExtendedClass(Class) setExtendedClass}(optionalExtendedClass);
     * cbe.{@link #setImplementedInterfaces(Class[]) setImplementedInterfaces}(implementedInterfaces);
     * cbe.{@link #setParentClassLoader(ClassLoader) setParentClassLoader}(optionalParentClassLoader);
     * cbe.{@link IClassBodyEvaluator#createInstance(Reader) createInstance}(reader);
     * </pre>
     *
     * @see #createInstance(Reader)
     */
    public static Object createFastClassBodyEvaluator(
        Scanner     scanner,
        String      className,
        Class       optionalExtendedClass,
        Class[]     implementedInterfaces,
        ClassLoader optionalParentClassLoader
    ) throws CompileException, ParseException, ScanException, IOException {
        ClassBodyEvaluator cbe = new ClassBodyEvaluator();
        cbe.setClassName(className);
        cbe.setExtendedClass(optionalExtendedClass);
        cbe.setImplementedInterfaces(implementedInterfaces);
        cbe.setParentClassLoader(optionalParentClassLoader);
        cbe.cook(scanner);
        Class c = cbe.getClazz();
        try {
            return c.newInstance();
        } catch (InstantiationException e) {
            throw new CompileException("Cannot instantiate abstract class -- one or more method implementations are missing", null);
        } catch (IllegalAccessException e) {
            // SNO - type and default constructor of generated class are PUBLIC.
            throw new JaninoRuntimeException(e.toString());
        }
    }
}
