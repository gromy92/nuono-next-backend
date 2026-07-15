package com.nuono.next.operationsskin;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OperationsSkinControllerTest {
    private static final String STORE_CODE = "STR108065-NAE";
    private static final String OTHER_STORE_CODE = "STR999-TEST";
    private static final long MAX_IMAGE_BYTES = 8L * 1024L * 1024L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private BusinessAccessResolver accessResolver;

    @Mock
    private OperationsSkinService service;

    @TempDir
    private Path uploadRoot;

    @TempDir
    private Path externalDir;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        System.setProperty(OperationsSkinAssetFileSupport.UPLOAD_DIR_PROPERTY, uploadRoot.toString());
        mvc = MockMvcBuilders
                .standaloneSetup(new OperationsSkinController(accessResolver, service))
                .build();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(OperationsSkinAssetFileSupport.UPLOAD_DIR_PROPERTY);
    }

    @Test
    void listRequiresStoreCode() throws Exception {
        mvc.perform(get("/api/operations/skin-management/skins"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accessResolver, service);
    }

    @Test
    void listWithStoreCodeResolvesAccessAndReturnsSkins() throws Exception {
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        when(service.list(context, STORE_CODE, "高级", "ACTIVE"))
                .thenReturn(List.of(skin(1001L, "白底高级感")));

        mvc.perform(get("/api/operations/skin-management/skins")
                        .param("storeCode", STORE_CODE)
                        .param("keyword", "高级")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1001))
                .andExpect(jsonPath("$[0].skinName").value("白底高级感"));

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verify(service).list(context, STORE_CODE, "高级", "ACTIVE");
    }

    @Test
    void createReturnsSavedSkinAndResolvesAccessFromBodyStoreCode() throws Exception {
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        when(service.create(eq(context), any(OperationsSkinSaveRequest.class)))
                .thenReturn(skin(1002L, "自然光生活感"));

        mvc.perform(post("/api/operations/skin-management/skins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeCode\":\"STR108065-NAE\",\"skinName\":\"自然光生活感\",\"status\":\"ACTIVE\",\"assets\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1002))
                .andExpect(jsonPath("$.storeCode").value(STORE_CODE))
                .andExpect(jsonPath("$.skinName").value("自然光生活感"));

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verify(service).create(eq(context), any(OperationsSkinSaveRequest.class));
    }

    @Test
    void createRequiresStoreCode() throws Exception {
        mvc.perform(post("/api/operations/skin-management/skins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skinName\":\"缺店铺\",\"status\":\"ACTIVE\",\"assets\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accessResolver, service);
    }

    @Test
    void serviceValidationErrorsMapToBadRequest() throws Exception {
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        when(service.create(eq(context), any(OperationsSkinSaveRequest.class)))
                .thenThrow(new IllegalArgumentException("皮肤名称不能为空。"));

        mvc.perform(post("/api/operations/skin-management/skins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeCode\":\"STR108065-NAE\",\"skinName\":\" \",\"status\":\"ACTIVE\",\"assets\":[]}"))
                .andExpect(status().isBadRequest());

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verify(service).create(eq(context), any(OperationsSkinSaveRequest.class));
    }

    @Test
    void serviceNotFoundErrorsMapToNotFound() throws Exception {
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        when(service.update(eq(context), eq(1001L), any(OperationsSkinSaveRequest.class)))
                .thenThrow(new OperationsSkinNotFoundException("皮肤不存在或无权访问。"));

        mvc.perform(put("/api/operations/skin-management/skins/{id}", 1001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeCode\":\"STR108065-NAE\",\"skinName\":\"不存在\",\"status\":\"ACTIVE\",\"assets\":[]}"))
                .andExpect(status().isNotFound());

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verify(service).update(eq(context), eq(1001L), any(OperationsSkinSaveRequest.class));
    }

    @Test
    void detailResolvesAccessAndReturnsComponents() throws Exception {
        BusinessAccessContext context = context();
        OperationsSkinView view = skin(1001L, "PAPERSAY 黄框主图");
        view.setHeroComponentCount(4);
        view.setHeroComponentRequiredCount(4);
        view.setComponents(List.of(component("FRAME", "/api/operations/skin-management/assets/frame.png")));
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        when(service.detail(context, 1001L, STORE_CODE)).thenReturn(view);

        mvc.perform(get("/api/operations/skin-management/skins/{id}", 1001L)
                        .param("storeCode", STORE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1001))
                .andExpect(jsonPath("$.heroComponentCount").value(4))
                .andExpect(jsonPath("$.components[0].componentKey").value("FRAME"))
                .andExpect(jsonPath("$.components[0].zIndex").value(40));

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verify(service).detail(context, 1001L, STORE_CODE);
    }

    @Test
    void updateComponentsResolvesAccessAndForwardsBody() throws Exception {
        BusinessAccessContext context = context();
        OperationsSkinView view = skin(1001L, "PAPERSAY 黄框主图");
        view.setHeroComponentCount(1);
        view.setHeroComponentRequiredCount(4);
        view.setComponents(List.of(component("FRAME", "/api/operations/skin-management/assets/frame.png")));
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        when(service.saveComponents(eq(context), eq(1001L), any(OperationsSkinComponentsSaveRequest.class)))
                .thenReturn(view);

        mvc.perform(put("/api/operations/skin-management/skins/{id}/components", 1001L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeCode\":\"STR108065-NAE\",\"components\":[{\"templateRole\":\"HERO_MAIN\",\"componentKey\":\"FRAME\",\"imageUrl\":\"/api/operations/skin-management/assets/frame.png\",\"x\":0,\"y\":0,\"width\":1247,\"height\":1706,\"zIndex\":40,\"required\":true,\"locked\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1001))
                .andExpect(jsonPath("$.heroComponentCount").value(1))
                .andExpect(jsonPath("$.components[0].componentKey").value("FRAME"));

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verify(service).saveComponents(eq(context), eq(1001L), any(OperationsSkinComponentsSaveRequest.class));
    }

    @Test
    void uploadRejectsNonImageFile() throws Exception {
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "x".getBytes(StandardCharsets.UTF_8)
        );

        mvc.perform(multipart("/api/operations/skin-management/assets")
                        .file(file)
                        .param("storeCode", STORE_CODE))
                .andExpect(status().isBadRequest());

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verifyNoInteractions(service);
    }

    @Test
    void uploadRejectsUnsupportedImageSubtype() throws Exception {
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "vector.svg",
                "image/svg+xml",
                "<svg/>".getBytes(StandardCharsets.UTF_8)
        );

        mvc.perform(multipart("/api/operations/skin-management/assets")
                        .file(file)
                        .param("storeCode", STORE_CODE))
                .andExpect(status().isBadRequest());

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verifyNoInteractions(service);
    }

    @Test
    void uploadRejectsEmptyFile() throws Exception {
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        mvc.perform(multipart("/api/operations/skin-management/assets")
                        .file(file)
                        .param("storeCode", STORE_CODE))
                .andExpect(status().isBadRequest());

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verifyNoInteractions(service);
    }

    @Test
    void uploadRejectsFilesLargerThanEightMb() throws Exception {
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                new byte[(int) MAX_IMAGE_BYTES + 1]
        );

        mvc.perform(multipart("/api/operations/skin-management/assets")
                        .file(file)
                        .param("storeCode", STORE_CODE))
                .andExpect(status().isBadRequest());

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verifyNoInteractions(service);
    }

    @Test
    void uploadStoresImageAndReturnsLocalAssetUrl() throws Exception {
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[] {1, 2, 3}
        );

        MvcResult result = mvc.perform(multipart("/api/operations/skin-management/assets")
                        .file(file)
                        .param("storeCode", STORE_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.filename").exists())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.size").value(3))
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {
                }
        );
        String filename = String.valueOf(body.get("filename"));
        try {
            org.junit.jupiter.api.Assertions.assertTrue(filename.endsWith(".png"));
            org.junit.jupiter.api.Assertions.assertEquals(
                    "/api/operations/skin-management/assets/" + filename + "?storeCode=STR108065-NAE",
                    body.get("url")
            );
        } finally {
            deleteUploaded(filename);
        }

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verifyNoInteractions(service);
    }

    @Test
    void uploadRejectsStoreSymlinkDirectory() throws Exception {
        createSymlinkOrSkip(storeUploadDir(STORE_CODE), externalDir);
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[] {1, 2, 3}
        );

        mvc.perform(multipart("/api/operations/skin-management/assets")
                        .file(file)
                        .param("storeCode", STORE_CODE))
                .andExpect(status().isBadRequest());

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verifyNoInteractions(service);
    }

    @Test
    void assetReadRequiresSameStoreAccessAndReturnsInlineResource() throws Exception {
        BusinessAccessContext context = context();
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context);
        when(accessResolver.requireBusinessContext(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT)))
                .thenReturn(context);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[] {7, 8, 9}
        );

        MvcResult upload = mvc.perform(multipart("/api/operations/skin-management/assets")
                        .file(file)
                        .param("storeCode", STORE_CODE))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(
                upload.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {
                }
        );
        String filename = String.valueOf(body.get("filename"));
        try {
            mvc.perform(get("/api/operations/skin-management/assets/{filename}", filename)
                            .param("storeCode", STORE_CODE))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", "inline"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(content().bytes(new byte[] {7, 8, 9}));
        } finally {
            deleteUploaded(filename);
        }

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verify(accessResolver).requireBusinessContext(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT));
        verify(service).verifyReadableAssetStore(context, STORE_CODE);
    }

    @Test
    void assetReadRequiresStoreCode() throws Exception {
        mvc.perform(get("/api/operations/skin-management/assets/{filename}", "missing.png"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accessResolver, service);
    }

    @Test
    void assetReadWithDifferentStoreDoesNotServeExistingFile() throws Exception {
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(context());
        BusinessAccessContext otherStoreContext = context(OTHER_STORE_CODE);
        when(accessResolver.requireBusinessContext(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT)))
                .thenReturn(otherStoreContext);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[] {4, 5, 6}
        );

        MvcResult upload = mvc.perform(multipart("/api/operations/skin-management/assets")
                        .file(file)
                        .param("storeCode", STORE_CODE))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(
                upload.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {
                }
        );
        String filename = String.valueOf(body.get("filename"));
        try {
            mvc.perform(get("/api/operations/skin-management/assets/{filename}", filename)
                            .param("storeCode", OTHER_STORE_CODE))
                    .andExpect(status().isNotFound());
        } finally {
            deleteUploaded(filename);
        }

        verify(accessResolver).requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE));
        verify(accessResolver).requireBusinessContext(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT));
        verify(service).verifyReadableAssetStore(otherStoreContext, OTHER_STORE_CODE);
    }

    @Test
    void assetReadCanUseOriginalStoreDirectoryThroughSiblingSiteAuthorization() throws Exception {
        BusinessAccessContext originContext = context();
        BusinessAccessContext siblingContext = context(OTHER_STORE_CODE);
        when(accessResolver.requireStoreAccess(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT), eq(STORE_CODE)))
                .thenReturn(originContext);
        when(accessResolver.requireBusinessContext(any(HttpServletRequest.class), eq(BusinessCapability.OPERATIONS_SKIN_MANAGEMENT)))
                .thenReturn(siblingContext);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[] {10, 11, 12}
        );

        MvcResult upload = mvc.perform(multipart("/api/operations/skin-management/assets")
                        .file(file)
                        .param("storeCode", STORE_CODE))
                .andExpect(status().isOk())
                .andReturn();
        Map<String, Object> body = objectMapper.readValue(
                upload.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {
                }
        );
        String filename = String.valueOf(body.get("filename"));
        try {
            mvc.perform(get("/api/operations/skin-management/assets/{filename}", filename)
                            .param("storeCode", STORE_CODE))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(new byte[] {10, 11, 12}));
        } finally {
            deleteUploaded(filename);
        }

        verify(service).verifyReadableAssetStore(siblingContext, STORE_CODE);
    }

    @Test
    void supportRejectsDotStoreDirectorySegments() {
        org.junit.jupiter.api.Assertions.assertEquals("_", OperationsSkinAssetFileSupport.safeStoreCode("."));
        org.junit.jupiter.api.Assertions.assertEquals("_", OperationsSkinAssetFileSupport.safeStoreCode(".."));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> OperationsSkinAssetFileSupport.storeUploadDir(".")
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> OperationsSkinAssetFileSupport.storeUploadDir("..")
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                OperationsSkinAssetFileSupport.storeUploadDir(STORE_CODE)
                        .normalize()
                        .startsWith(uploadDir().normalize())
        );
    }

    @Test
    void supportRejectsRootSymlinkDirectory() throws Exception {
        Path symlinkRoot = uploadRoot.resolveSibling(uploadRoot.getFileName() + "-link");
        createSymlinkOrSkip(symlinkRoot, externalDir);
        System.setProperty(OperationsSkinAssetFileSupport.UPLOAD_DIR_PROPERTY, symlinkRoot.toString());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> OperationsSkinAssetFileSupport.ensureStoreUploadDir(STORE_CODE)
        );
    }

    @Test
    void assetReadRejectsDotDotStoreCodeEvenWhenResolverApprovesIt() throws Exception {
        mvc.perform(get("/api/operations/skin-management/assets/{filename}", "sample.png")
                        .param("storeCode", ".."))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accessResolver, service);
    }

    @Test
    void assetRejectsUnsafeFilename() throws Exception {
        mvc.perform(get("/api/operations/skin-management/assets/{filename}", "..evil.png"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accessResolver, service);
    }

    private OperationsSkinView skin(Long id, String skinName) {
        OperationsSkinView view = new OperationsSkinView();
        view.setId(id);
        view.setStoreCode(STORE_CODE);
        view.setSkinName(skinName);
        view.setStatus(OperationsSkinStatus.ACTIVE.name());
        view.setCoverImageUrl("/api/operations/skin-management/assets/sample.png");
        view.setAssetCount(0);
        view.setAssets(List.of());
        return view;
    }

    private OperationsSkinComponentView component(String componentKey, String imageUrl) {
        OperationsSkinComponentView component = new OperationsSkinComponentView();
        component.setTemplateRole("HERO_MAIN");
        component.setComponentKey(componentKey);
        component.setImageUrl(imageUrl);
        component.setZIndex(40);
        component.setRequired(true);
        component.setLocked(true);
        return component;
    }

    private BusinessAccessContext context() {
        return context(STORE_CODE);
    }

    private BusinessAccessContext context(String storeCode) {
        return BusinessAccessContext.builder()
                .sessionUserId(90003L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of(storeCode))
                .storeOwnerUserIds(Map.of(storeCode, 307L))
                .menuPaths(Set.of("/operations/skin-management"))
                .build();
    }

    private Path uploadDir() {
        return OperationsSkinAssetFileSupport.uploadDir();
    }

    private Path storeUploadDir(String storeCode) {
        return OperationsSkinAssetFileSupport.storeUploadDir(storeCode);
    }

    private void deleteUploaded(String filename) throws Exception {
        Files.deleteIfExists(uploadDir().resolve(filename));
        Files.deleteIfExists(storeUploadDir(STORE_CODE).resolve(filename));
        Files.deleteIfExists(storeUploadDir(OTHER_STORE_CODE).resolve(filename));
    }

    private void createSymlinkOrSkip(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are not supported");
        }
    }
}
