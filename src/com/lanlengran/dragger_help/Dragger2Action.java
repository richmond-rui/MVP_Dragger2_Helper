package com.lanlengran.dragger_help;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.text.SimpleDateFormat;

public class Dragger2Action extends BaseGenerateAction {
    protected JFrame mDialog;
    protected static final Logger log = Logger.getInstance(Dragger2Action.class);
    private PsiDirectory mMVPDir;
    private PsiFile mFile;
    private PsiClass mClass;
    private String presenterName;
    private String modelName;
    private PsiElementFactory mFactory;
    private String viewIName;
    private String viewName;

    public Dragger2Action() {
        super(null);
    }

    public Dragger2Action(CodeInsightActionHandler handler) {
        super(handler);
    }

    @Override
    protected boolean isValidForClass(final PsiClass targetClass) {

        return false;
    }

    @Override
    public boolean isValidForFile(Project project, Editor editor, PsiFile file) {

        return true;
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        // TODO: insert action logic here
        //获取当前点击工程
        Project project = event.getData(PlatformDataKeys.PROJECT);
        Editor editor = event.getData(PlatformDataKeys.EDITOR);

        actionPerformedImpl(project, editor);
    }

    @Override
    public void actionPerformedImpl(@NotNull Project project, Editor editor) {


        mFile = PsiUtilBase.getPsiFileInEditor(editor, project);
        mClass = getTargetClass(editor, mFile);
        if (mClass.getName() == null) {
            return;
        }
        log.info("mClass=====" + mClass.getName());
        mFactory = JavaPsiFacade.getElementFactory(project);
        mMVPDir = createMVPDir(); //创建mvp文件夹
        viewName = mClass.getName();

        creatMVPFile();
        writeActivity(project, mClass);

        Module module = ModuleUtil.findModuleForPsiElement(mFile);
        GlobalSearchScope moduleScope = module.getModuleWithDependenciesAndLibrariesScope(false);


        createActivityComponent(project, moduleScope);
        createActivityModule(project, moduleScope);
    }

    private void createActivityModule(@NotNull Project project, GlobalSearchScope moduleScope) {
        PsiClass[] activityComponentClasses = PsiShortNamesCache.getInstance(project).getClassesByName("ActivityModule", moduleScope);
        if (activityComponentClasses.length > 0) {
            PsiClass activityComponentClass = activityComponentClasses[0];
            WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                @Override
                public void run() {
                    String lowCaseViewName = Util_File.stringFirstToLowerCase(viewName);
                    activityComponentClass.addBefore(mFactory.createFieldFromText("private " + viewName + " " + lowCaseViewName + ";", activityComponentClass), activityComponentClass.getMethods()[0]);
                    activityComponentClass.addBefore(mFactory.createMethodFromText("public ActivityModule(" + viewName + " arg) {" +
                            "this." + lowCaseViewName + " = arg;" +
                            "}", activityComponentClass), activityComponentClass.getRBrace());
                    activityComponentClass.addBefore(mFactory.createAnnotationFromText("@Singleton",activityComponentClass),activityComponentClass.getRBrace());
                    activityComponentClass.addBefore(mFactory.createAnnotationFromText("@Provides",activityComponentClass),activityComponentClass.getRBrace());
                    activityComponentClass.addBefore(mFactory.createMethodFromText("public "+presenterName+" get"+presenterName+"() {" +
                            "return new "+presenterName+"("+lowCaseViewName+");" +
                            "}",activityComponentClass),activityComponentClass.getRBrace());
                }
            });
        }
    }

    private void createActivityComponent(@NotNull Project project, GlobalSearchScope moduleScope) {
        PsiClass[] activityComponentClasses = PsiShortNamesCache.getInstance(project).getClassesByName("ActivityComponent", moduleScope);
        if (activityComponentClasses.length > 0) {
            PsiClass activityComponentClass = activityComponentClasses[0];
            WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                @Override
                public void run() {
                    activityComponentClass.addBefore(mFactory.createMethodFromText(modelName + " get" + modelName + "();", activityComponentClass), activityComponentClass.getRBrace());
                    activityComponentClass.addBefore(mFactory.createMethodFromText(viewName + " inject(" + viewName + " mView);", activityComponentClass), activityComponentClass.getRBrace());
                }
            });
        }
    }

    /**
     * 修改activity
     *
     * @param project
     * @param mClass
     */
    private void writeActivity(@NotNull Project project, PsiClass mClass) {

        WriteCommandAction.runWriteCommandAction(project, new Runnable() {
            @Override
            public void run() {

                PsiReferenceList list = mClass.getImplementsList();
                list.add(mFactory.createReferenceFromText(viewIName,mClass));


                mClass.add(mFactory.createMethodFromText("@Override  public BaseActivity getActivity() {return this;}", mClass));
                mClass.addBefore(mFactory.createFieldFromText("private ActivityComponent activityComponent;", mClass), mClass.getMethods()[0]);
                mClass.addBefore(mFactory.createAnnotationFromText("@Inject", mClass), mClass.getMethods()[0]);
                mClass.addBefore(mFactory.createFieldFromText("protected " + presenterName + " mPrenseter;", mClass), mClass.getMethods()[0]);
                mClass.add(mFactory.createMethodFromText("@Override" +
                        "    public ActivityComponent getActivityComponent() {" +
                        "        return activityComponent;" +
                        "    }", mClass));
                mClass.add(mFactory.createMethodFromText("@Override" +
                        "    public void setComponent() {" +
                        "        activityComponent = DaggerActivityComponent.builder()" +
                        "                .myApplicationComponent(MyApplication.getApplication().getMyApplicationComponent())" +
                        "                .activityModule(new ActivityModule(this))" +
                        "                .build();" +
                        "        activityComponent.inject(this);" +
                        "    }", mClass));
            }
        });
    }

    private PsiDirectory createMVPDir() {
        PsiDirectory mvpDir = mFile.getParent().findSubdirectory("mvp");
        if (mvpDir == null) {
            mvpDir = mFile.getParent().createSubdirectory("mvp");
        }

        return mvpDir;
    }

    private void creatMVPFile() {

        viewIName = mClass.getName() + "ViewI";
        modelName = mClass.getName() + "Model";
        presenterName = mClass.getName() + "Presenter";

        log.info("mClass=====" + mClass.getName());
        boolean hasModel = false;
        boolean hasPresenter = false;
        boolean hasViewI = false;
        for (PsiFile f : mMVPDir.getFiles()) {
            if (f.getName().contains("Model")) {
                String realName = f.getName().split("Model")[0];
                if (mClass.getName().contains(realName)) {
                    hasModel = true;
                    modelName = f.getName().replace(".java", "");
                }
            }

            if (f.getName().contains("Presenter")) {
                String realName = f.getName().split("Presenter")[0];
                if (mClass.getName().contains(realName)) {
                    hasPresenter = true;
                    presenterName = f.getName().replace(".java", "");
                }
            }

            if (f.getName().contains("ViewI")) {
                String realName = f.getName().split("ViewI")[0];
                if (mClass.getName().contains(realName)) {
                    hasViewI = true;
                    viewIName = f.getName().replace(".java", "");
                }
            }
        }

        Module module = ModuleUtil.findModuleForPsiElement(mFile);
        if (module == null) {
            return;
        }

        if (!hasPresenter) {
            createPresenter(module, presenterName, viewIName, modelName);
        }
        if (!hasViewI) {
            createViewI(viewIName);
        }
        if (!hasModel) {
            createModel(modelName);
        }
    }

    private void createViewI(String viewIName) {
        PsiFile viewIFile = mMVPDir.createFile(viewIName + ".java");

        StringBuffer modelText = new StringBuffer();
        modelText.append("package " + AndroidUtils.getFilePackageName(mMVPDir.getVirtualFile()) + ";\n\n\n");
        modelText.append(getHeaderAnnotation() + "\n");
        modelText.append("public interface " + viewIName + " extends BaseViewI{\n\n\n");
        modelText.append("}");

        Util_File.string2Stream(modelText.toString(), viewIFile.getVirtualFile().getPath());
    }

    private void createModel(String modelName) {
        PsiFile ModelFile = mMVPDir.createFile(modelName + ".java");

        StringBuffer modelText = new StringBuffer();
        modelText.append("package " + AndroidUtils.getFilePackageName(mMVPDir.getVirtualFile()) + ";\n\n\n");
        modelText.append(getHeaderAnnotation() + "\n");
        modelText.append("public class " + modelName + " extends BaseModel{\n\n\n");
        modelText.append("    @Inject\n" +
                "    public " + modelName + "() {\n" +
                "    }");
        modelText.append("}");

        Util_File.string2Stream(modelText.toString(), ModelFile.getVirtualFile().getPath());
    }

    private void createPresenter(Module module, String presenterName, String viewIName, String modelName) {


        PsiFile presenterFile = mMVPDir.createFile(presenterName + ".java");


        StringBuffer modelText = new StringBuffer();
        modelText.append("package " + AndroidUtils.getFilePackageName(mMVPDir.getVirtualFile()) + ";\n\n\n");

        modelText.append(getHeaderAnnotation() + "\n");

        modelText.append("public class " + presenterName + " extends BasePresenter{\n\n\n");
        modelText.append(viewIName + " mView;\n");
        modelText.append(" @Inject\n");
        modelText.append(modelName + " mModel;\n");
        modelText.append("   public " + presenterName + "(" + viewIName + " arg) {\n" +
                "        super(arg);\n" +
                "        this.mView = arg;\n" +
                "        this.mModel = this.mView.getActivityComponent().get" + modelName + "();\n" +
                "\n" +
                "    }\n");
        modelText.append("    @Override\n" +
                "    public BaseModel getBaseModel() {\n" +
                "        return mModel;\n" +
                "    }");
        modelText.append("}");

        Util_File.string2Stream(modelText.toString(), presenterFile.getVirtualFile().getPath());


    }

    private String getHeaderAnnotation() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String time = sdf.format(System.currentTimeMillis());
        String annotation = "/**\n" +
                " * Created  on " + time + ".\n" +
                " */";
        return annotation;
    }

}
