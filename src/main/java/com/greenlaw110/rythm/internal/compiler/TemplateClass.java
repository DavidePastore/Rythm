package com.greenlaw110.rythm.internal.compiler;

import com.greenlaw110.rythm.Rythm;
import com.greenlaw110.rythm.RythmEngine;
import com.greenlaw110.rythm.internal.CodeBuilder;
import com.greenlaw110.rythm.internal.CodeGenerator;
import com.greenlaw110.rythm.logger.ILogger;
import com.greenlaw110.rythm.logger.Logger;
import com.greenlaw110.rythm.resource.ITemplateResource;
import com.greenlaw110.rythm.spi.ITemplateClassEnhancer;
import com.greenlaw110.rythm.template.ITemplate;
import com.greenlaw110.rythm.template.TemplateBase;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Define the data structure hold template class/template src/generated java src
 */
public class TemplateClass {
    private static AtomicLong nextVersion = new AtomicLong();    
    private static final ILogger logger = Logger.get(TemplateClass.class);

    /**
     * Store root level template class, e.g. the one that is not an embedded class
     */
    private TemplateClass root;
    public TemplateClass root() {
        return root;
    }

    private TemplateClass() {}
    private boolean inner = false;
    public static TemplateClass createInnerClass(String className, byte[] byteCode, TemplateClass parent) {
        TemplateClass tc = new TemplateClass();
        tc.name = className;
        tc.javaByteCode = byteCode;
        tc.inner = true;
        tc.root = parent.root();
        tc.version = parent.version();
        return tc;
    }
    public boolean isInner() {return inner;}
    
    private RythmEngine engine = null;
    private RythmEngine engine() {
        return null == engine ? Rythm.engine : engine;
    }

    /**
     * The fully qualified class name
     */
    private String name;
    public String name0() {
        return name;
    }
    public String name() {
        return isInner() ? name : name + "v" + version;
    }
    private long version;
    public long version() {
        return root().version;
    }
    public TemplateClass extendedTemplateClass;
    /**
     * If not null then this template is a tag
     */
    public String tagName;
    /**
     * the template resource
     */
    public ITemplateResource templateResource;
    /**
     * The Java source
     */
    public String javaSource;
    /**
     * The compiled byteCode
     */
    public byte[] javaByteCode;
    /**
     * The enhanced byteCode
     */
    public byte[] enhancedByteCode;
    /**
     * The in JVM loaded class
     */
    public Class<ITemplate> javaClass;
    /**
     * The in JVM loaded package
     */
    public Package javaPackage;
    /**
     * Is this class compiled
     */
    boolean compiled;
    /**
     * Signatures checksum
     */
    public int sigChecksum;

    /**
     * The ITemplate instance
     */
    public TemplateBase templateInstance;

    private TemplateClass(RythmEngine engine) {
        this.engine = null == engine ? null : engine.isSingleton() ? null : engine;
    }
    
    /**
     * Construct a TemplateClass instance using template source file
     *
     * @param file the template source file
     */
    public TemplateClass(File file, RythmEngine engine) {
        this(engine);
        templateResource = engine.resourceManager.get(file);
        refresh();
    }

    /**
     * Construct a TemplateClass instance using template source content or file path
     * @param template
     */
    public TemplateClass(String template, RythmEngine engine) {
        this(engine);
        templateResource = engine().resourceManager.get(template);
        refresh();
    }

    /**
     * Return string representation of the template
     * @return
     */
    public String getKey() {
        return null == templateResource ? name() : templateResource.getKey();
    }
    
    private Class<?> getJavaClass() throws Exception {
        return engine().classLoader.loadClass(name(), true);
    }

    private ITemplate templateInstance_() {
        if (null == templateInstance) {
            try {
                Class<?> clz = getJavaClass();
                templateInstance = (TemplateBase) clz.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        // check parent class change
        Class<?> c = templateInstance.getClass();
        Class<?> pc = c.getSuperclass();
        if (null != pc && !TemplateBase.class.equals(pc)) {
            engine().classes.getByClassName(pc.getName());
        }
        return templateInstance;
    }
    
    public ITemplate asTemplate(StringBuilder out) {
        return templateInstance_().cloneMe(engine, out);
    }

    public ITemplate asTemplate() {
        return templateInstance_().cloneMe(engine, null);
    }
    
    private boolean refreshing = false;
    private boolean compiling = false;
    private boolean refreshing() {
        return refreshing || compiling;
    }
    
    private void addVersion() {
        TemplateClassCache tcc = engine().classes;
        tcc.clsNameIdx.remove(name());
        //List<TemplateClass> allEmbedded = tcc.getEmbeddedClasses(name0());
        version = nextVersion.getAndIncrement();
        tcc.clsNameIdx.put(name(), this);
    }

    /**
     * Need to refresh this class?
     */
    public boolean  refresh() {
        if (refreshing()) return false;
        try {
            refreshing = true;
            if (null == name) {
                // this is the root level template class
                root = this;
                name = templateResource.getSuggestedClassName() + CN_SUFFIX;
                version = nextVersion.getAndIncrement();
                engine().classes.add(this);
            }

            if (null == templateResource) {
                return false; // it must be inner class
            }

            boolean extendedTemplateModified = false;
            if (extendedTemplateClass != null) extendedTemplateModified = extendedTemplateClass.refresh();
            boolean modified = extendedTemplateModified || templateResource.refresh();
            if (!modified) return false;
            addVersion();
            long start = System.currentTimeMillis();
            CodeBuilder cb = new CodeBuilder(templateResource.asTemplateContent(), name(), tagName, engine);
            javaSource = cb.build().toString();
            extendedTemplateClass = cb.getExtendedTemplateClass();
            //if (logger.isTraceEnabled()) logger.trace(javaSource);
            logger.info(javaSource);
            //System.err.println(javaSource);
            if (logger.isTraceEnabled()) {
                logger.trace("%s ms to generate java source for template: %s", System.currentTimeMillis() - start, getKey());
            }
            javaByteCode = null;
            enhancedByteCode = null;
            templateInstance = null;
            javaClass = null;
            compiled = false;
            return true;
        } finally {
            refreshing = false;
        }
    }

    public static final String CN_SUFFIX = "__R_T_C__";

    /**
     * Is this class already compiled but not defined ?
     * @return if the class is compiled but not defined
     */
    public boolean isDefinable() {
        return compiled && javaClass != null;
    }

    /**
     * Compile the class from Java source
     * @return the bytes that comprise the class file
     */
    public byte[] compile() {
        if (null != javaByteCode) return javaByteCode;
        compiling = true;
        long start = System.currentTimeMillis();
        try {
            engine().classes.compiler.compile(new String[]{name()});
        } catch (RuntimeException e) {
            logger.error(javaSource);
            throw e;
        } finally {
            compiling = false;
        }

        if (logger.isTraceEnabled()) logger.trace("%sms to compile template class %s", System.currentTimeMillis() - start, getKey());

        return javaByteCode;
    }

    private boolean enhancing = false;
    public byte[] enhance() {
        if (enhancing) throw new IllegalStateException("reenter enhance() call");
        enhancing = true;
        try {
            byte[] bytes = enhancedByteCode;
            if (null == bytes) { bytes = compile(); }
            long start = System.currentTimeMillis();
            for (ITemplateClassEnhancer en: engine().templateClassEnhancers) {
                try {
                    bytes = en.enhance(name(), bytes);
                } catch (Exception e) {
                    logger.warn(e, "Error enhancing template class: %s", getKey());
                }
            }
            if (logger.isTraceEnabled()) {
                String id = (null == templateResource) ? name(): getKey();
                logger.trace("%sms to enhance template class %s", System.currentTimeMillis() - start, id);
            }
            enhancedByteCode = bytes;
            return bytes;
        } finally {
            enhancing = false;
        }
    }

    /**
     * Unload the class
     */
    public void uncompile() {
        javaClass = null;
    }

    public boolean isClass() {
        return !name().endsWith("package-info");
    }

    public String getPackage() {
        int dot = name().lastIndexOf('.');
        return dot > -1 ? name().substring(0, dot) : "";
    }

    /**
     * Call back when a class is compiled.
     * @param code The bytecode.
     */
    public void compiled(byte[] code) {
        javaByteCode = code;
        enhancedByteCode = code;
        compiled = true;
    }

    @Override
    public String toString() {
        return "(compiled:" + compiled + ") " + name();
    }
}
