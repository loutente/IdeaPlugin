package org.axonframework.intellij.ide.plugin;

import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import org.axonframework.intellij.ide.plugin.handler.*;
import org.axonframework.intellij.ide.plugin.publisher.EventPublisher;
import org.axonframework.intellij.ide.plugin.publisher.EventPublisherRepository;
import org.axonframework.intellij.ide.plugin.publisher.EventPublisherRepositoryImpl;
import org.axonframework.intellij.ide.plugin.publisher.ExtractEventPublisherMethodArgumentVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * This class shows an icon in the gutter when an Axon annotation is found. The icon can be used to navigate to all
 * classes that handle the event.
 */
public class AxonGutterAnnotator implements Annotator {

    public static final Icon AxonIconIn = IconLoader.getIcon("/icons/axon_into.png"); // 10x14
    public static final Icon AxonIconOut = IconLoader.getIcon("/icons/axon_publish.png"); // 10x14

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof PsiExpression && element.getText().contains("apply")) {
            PsiSearchHelper psiSearchHelper = PsiSearchHelper.SERVICE.getInstance(element.getProject());
            AxonEventHandlerProcessor axonEventHandlerProcessor = new AxonEventHandlerProcessor(element);
            psiSearchHelper.processAllFilesWithWord("EventHandler",
                    GlobalSearchScope.allScope(element.getProject()),
                    axonEventHandlerProcessor,
                    true);
            findEventHandlers(element, holder, axonEventHandlerProcessor);
        }
    }

    public static void findEventHandlers(PsiElement psiElement, AnnotationHolder holder, AxonEventHandlerProcessor axonEventHandlerProcessor) {
        Collection<PsiMethod> psiMethods = axonEventHandlerProcessor.getHandlerRepository()
                                                                         .getAllHandlerPsiElements();
        createGutterIconToEventHandlers(psiElement, holder, psiMethods);

        for (EventHandler eventHandler : axonEventHandlerProcessor.getHandlerRepository().getAllHandlers()) {
            PsiElement element = eventHandler.getElementForAnnotation();
            if (element.getContainingFile().isEquivalentTo(psiElement.getContainingFile())) {
                createGutterIconToEventPublishers(element,
                                                  holder,
                                                  axonEventHandlerProcessor.getPublisherRepository()
                                                                           .getPublisherPsiElementsFor(eventHandler
                                                                                                               .getHandledType()));
            }
        }
    }

    private static void createGutterIconToEventPublishers(PsiElement psiElement, AnnotationHolder holder,
                                                          Collection<PsiElement> targets) {
        if (!targets.isEmpty()) {
            NavigationGutterIconBuilder.create(AxonIconIn)
                                       .setTargets(targets)
                                       .setPopupTitle("Publishers")
                                       .setCellRenderer(new MethodCellRenderer(true))
                                       .setTooltipText("Navigate to the publishers of this event")
                                       .install(holder, psiElement);
        }
    }

    private static void createGutterIconToEventHandlers(PsiElement psiElement, AnnotationHolder holder,
                                                        Collection<? extends PsiElement> targets) {
        if (!targets.isEmpty()) {
            NavigationGutterIconBuilder.create(AxonIconOut)
                                       .setTargets(targets)
                                       .setPopupTitle("Event Handlers")
                                       .setCellRenderer(new MethodCellRenderer(true))
                                       .setTooltipText("Navigate to the handlers for this event")
                                       .install(holder, psiElement);
        }
    }


    private static class AxonEventHandlerProcessor implements Processor<PsiFile> {

        private final PsiElement psiElement;
        private EventPublisherRepository publisherRepository = new EventPublisherRepositoryImpl();
        private EventHandlerRepository handlerRepository = new EventHandlerRepositoryImpl();

        public AxonEventHandlerProcessor(PsiElement psiElement) {
            this.psiElement = psiElement;
        }

        @Override
        public boolean process(PsiFile psiFile) {
            Collection<PsiAnnotation> parameterList = PsiTreeUtil.findChildrenOfType(psiFile.getNode().getPsi(),
                                                                                     PsiAnnotation.class);
            for (PsiAnnotation psiAnnotation : parameterList) {
                if (EventHandlerImpl.isEventHandlerAnnotation(psiAnnotation)) {
                    ExtractEventPublisherMethodArgumentVisitor eventPublisherVisitor = new ExtractEventPublisherMethodArgumentVisitor();
                    psiElement.accept(eventPublisherVisitor);
                    ExtractEventHandlerArgumentVisitor eventHandlerVisitor = new ExtractEventHandlerArgumentVisitor();
                    psiAnnotation.getParent().getParent().accept(eventHandlerVisitor);

                    EventPublisher eventPublisher = eventPublisherVisitor.getEventPublisher();
                    EventHandler eventHandler = eventHandlerVisitor.getEventHandler();
                    PsiType type = eventHandler.getHandledType();
                    if (eventPublisherVisitor.hasEventPublisher() && eventPublisher.canPublishType(type)) {
                        handlerRepository.addHandlerForType(type, eventHandler);
                        publisherRepository.addPublisherForType(type, eventPublisher);
                    }
                }
            }
            return true;
        }

        public EventPublisherRepository getPublisherRepository() {
            return publisherRepository;
        }

        public EventHandlerRepository getHandlerRepository() {
            return handlerRepository;
        }
    }
}
